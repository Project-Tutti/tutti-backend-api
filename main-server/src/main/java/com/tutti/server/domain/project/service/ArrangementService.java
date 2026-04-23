package com.tutti.server.domain.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutti.server.domain.project.dto.response.ProgressEvent;
import com.tutti.server.domain.project.entity.Project;
import com.tutti.server.domain.project.entity.ProjectVersion;
import com.tutti.server.domain.project.entity.VersionMapping;
import com.tutti.server.domain.project.repository.ProjectVersionRepository;
import com.tutti.server.domain.project.repository.VersionMappingRepository;
import com.tutti.server.global.common.ApiResponse;
import com.tutti.server.infra.ai.dto.AiArrangeRequest;
import com.tutti.server.infra.ai.dto.AiCallbackPayload;
import com.tutti.server.infra.converter.ConverterService;
import com.tutti.server.infra.storage.SupabaseStorageService;
import com.tutti.server.domain.instrument.entity.Instrument;
import com.tutti.server.domain.instrument.entity.InstrumentCategory;
import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import com.tutti.server.domain.instrument.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 편곡 서비스 — Redis 기반 비동기 통신 및 SSE 진행률 스트리밍을 관리합니다.
 *
 * <h3>아키텍처 위치</h3>
 *
 * <pre>
 * ProjectService → <b>ArrangementService</b> ──(Redis XADD)──→ AI 서버 Worker (XREADGROUP)
 *                                           ←──(HTTP 콜백)────← AI 서버
 *                        │
 *                  InternalCallbackController (콜백 수신)
 *                        │
 *                  콜백 수신 Pod: DB 업데이트 → Redis PUBLISH
 *                        │
 *               모든 Pod: Redis SUBSCRIBE → 로컬 emitter에 SSE 전달
 * </pre>
 *
 * <h3>핵심 동작 흐름</h3>
 * <ol>
 * <li>{@code requestArrangement()} — Redis 큐에 편곡 요청 발행 (LPUSH)</li>
 * <li>{@code subscribe()} — 클라이언트가 SSE 연결을 열고 실시간 진행률을 수신</li>
 * <li>{@code handleCallback()} — AI 서버가 진행 상황을 HTTP 콜백으로 전달</li>
 * <li>콜백 수신 → DB 업데이트 → Redis PUBLISH → 모든 Pod가 수신 → 로컬 emitter에 SSE 전달</li>
 * </ol>
 *
 * <h3>멀티 Pod SSE 동기화</h3>
 * SSE emitter는 각 Pod의 인메모리에 저장되지만, Redis Pub/Sub를 통해
 * 모든 Pod에 이벤트가 브로드캐스트되므로 어떤 Pod가 콜백을 수신하든
 * SSE 연결을 보유한 Pod로 이벤트가 전달됩니다.
 *
 * @see com.tutti.server.infra.redis.SseRedisSubscriber
 * @see com.tutti.server.infra.ai.InternalCallbackController
 * @see com.tutti.server.global.config.RedisConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArrangementService {

    /** SSE 연결 타임아웃 — 10분. AI 편곡은 보통 3~5분 소요됩니다. */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    /**
     * AI 서버와의 HTTP 통신을 담당하는 WebClient.
     * WebClientConfig에서 Bean으로 등록되며, 타임아웃과 버퍼 크기가 설정되어 있습니다.
     *
     * <p>
     * 왜 RestTemplate이 아닌 WebClient인가?
     * </p>
     * → WebClient는 비동기/논블로킹으로 동작하여, AI 서버 응답을 기다리는 동안
     * 다른 요청을 처리할 수 있습니다. RestTemplate은 동기/블로킹이므로
     * AI 서버 응답을 기다리는 동안 스레드가 점유됩니다.
     */
    private final WebClient aiWebClient;                 // 유지: 디버그/폴백용
    private final StringRedisTemplate redisTemplate;      // Redis: 큐잉 + Pub/Sub
    private final ObjectMapper objectMapper;              // JSON 직렬화
    private final ProjectVersionRepository versionRepository;
    private final VersionMappingRepository mappingRepository;
    private final SupabaseStorageService storageService;
    private final ConverterService converterService;
    private final InstrumentCategoryRepository categoryRepository;
    private final InstrumentRepository instrumentRepository;

    @Value("${ai.server.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${ai.server.callback-secret:default-callback-secret}")
    private String callbackSecret;

    /** Redis Pub/Sub 채널명 — SSE 이벤트 브로드캐스트용 */
    private static final String SSE_CHANNEL = "tutti:sse:progress";
    /** Redis Stream 키 — AI 편곡 요청 스트림 (Consumer Group 기반) */
    private static final String STREAM_KEY = "ai:arrange:stream";

    // ── SSE Emitter 저장소 ──
    // 키: "projectId:versionId" 복합 키 (방어적 코딩 + 디버깅 용이성)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    /** emitter 생성 시각 — 만료 정리 스케줄러에서 사용합니다. */
    private final Map<String, Long> emitterCreatedAt = new ConcurrentHashMap<>();

    /** projectId:versionId 복합 키 생성 — null 방어 포함 */
    private static String emitterKey(Long projectId, Long versionId) {
        // null 방어: 잘못된 payload에서도 안전한 키 생성
        String pId = (projectId != null) ? projectId.toString() : "unknown";
        String vId = (versionId != null) ? versionId.toString() : "unknown";
        return pId + ":" + vId;
    }

    // ══════════════════════════════════════════════════════
    // 만료 Emitter 정리 스케줄러
    // ══════════════════════════════════════════════════════

    /**
     * 만료된 SSE emitter를 주기적으로 정리합니다.
     *
     * <p>
     * 왜 필요한가? → AI 서버가 콜백을 보내지 않는 극한 상황(장애, 네트워크 두절)에서
     * emitter가 메모리에 무한히 누적되는 것을 방지합니다.
     * </p>
     *
     * <p>
     * 실행 주기: 60초마다. SSE 타임아웃(10분) + 60초 여유 후 정리합니다.
     * </p>
     *
     * @Scheduled(fixedDelay): 이전 실행이 완료된 후 60초 뒤에 다시 실행됩니다.
     * 사용하려면 @EnableScheduling이 메인 클래스에 선언되어 있어야 합니다.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupStaleEmitters() {
        long now = System.currentTimeMillis();
        emitterCreatedAt.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > SSE_TIMEOUT_MS + 60_000) {
                String key = entry.getKey();
                List<SseEmitter> list = emitters.remove(key);
                if (list != null) {
                    list.forEach(emitter -> {
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                    });
                }
                log.debug("만료 SSE emitter 정리: key={}", key);
                return true;
            }
            return false;
        });
    }

    /**
     * 무한 로딩 방지 (Garbage Collector) 스케줄러.
     *
     * <p>
     * AI 서버가 처리 도중 비정상 종료되거나 긴급 재작동해야 하는 경우,
     * DB에는 PROCESSING 상태로 영원히 남아 앱 성능을 저하시키거나 버그를 발생시킬 수 있습니다.
     * 이 스케줄러가 5분 단위로 돌면서, 마지막 생존 신고(updatedAt)로부터
     * 15분이 경과한 프로젝트를 찾아 자동으로 FAILED 상태로 강제 전환합니다.
     * </p>
     */
    @Scheduled(fixedDelay = 300_000) // 5분(300초)마다 실행
    @Transactional
    public void cleanupStuckProjects() {
        // 15분 전 기준선
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusMinutes(15);

        List<ProjectVersion> stuckVersions = versionRepository.findByStatusAndUpdatedAtBefore(
                ProjectVersion.VersionStatus.PROCESSING, threshold);

        if (!stuckVersions.isEmpty()) {
            log.warn("AI 서버 생존 확인 불가(가비지 컬렉터 작동): {}개의 버전을 FAILED 처리합니다.", stuckVersions.size());
            for (ProjectVersion version : stuckVersions) {
                version.updateStatus(ProjectVersion.VersionStatus.FAILED);
                // JPA 트랜잭션이 종료될 때 Dirty Checking으로 자동 update 쿼리 발생
                
                // 만일을 위해 실패 브로드캐스트 (프론트엔드 연결이 남아있을 경우 대비)
                publishSseEvent(ProgressEvent.builder()
                        .projectId(version.getProject().getId())
                        .versionId(version.getId())
                        .status("failed")
                        .progress(version.getProgress())
                        .build());
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // AI 서버 비동기 호출
    // ══════════════════════════════════════════════════════

    /**
     * Redis Stream에 편곡 요청을 발행합니다.
     *
     * <p>
     * AI 편곡은 3~5분이 걸리므로, Stream에 넣고 즉시 반환합니다.
     * 온프레미스 AI 서버의 Worker가 XREADGROUP으로 Consumer Group에서 1건씩 꺼내어 처리합니다.
     * </p>
     *
     * <pre>
     * 이 메서드 →(Redis XADD)→ "ai:arrange:stream"
     *   → AI Worker가 XREADGROUP으로 스트림에서 꺼냄
     *   → 편곡 완료 후 HTTP 콜백 →(POST)→ /internal/callback/arrange
     *     → handleCallback() → DB 저장 + Redis PUBLISH → SSE 전달
     * </pre>
     *
     * @param project 프로젝트 엔티티 (MIDI 파일 경로 포함)
     * @param version 편곡 버전 엔티티
     */
    public void requestArrangement(Project project, ProjectVersion version) {
        // 1. 버전의 매핑 정보를 AI 서버 요청 형식으로 변환
        List<VersionMapping> mappings = mappingRepository.findByVersionId(version.getId());

        List<AiArrangeRequest.MappingData> mappingDataList = mappings.stream()
                .map(m -> AiArrangeRequest.MappingData.builder()
                        .trackIndex(m.getTrackIndex())
                        .targetInstrumentId(m.getTargetInstrumentId())
                        .build())
                .toList();

        // 2. AI 서버 요청 DTO 구성 — Supabase Storage URL로 MIDI 파일 위치 전달
        String midiUrl = storageService.getPublicUrl(
                SupabaseStorageService.BUCKET_MIDI, project.getMidiFilePath());

        // 3. 카테고리 기반 modelType 결정
        Integer categoryProgram = resolveCategoryProgram(version.getInstrumentId());
        String modelType = resolveModelType(categoryProgram);
        String instrumentName = resolveInstrumentName(version.getInstrumentId());

        AiArrangeRequest request = AiArrangeRequest.builder()
                .projectId(project.getId())
                .versionId(version.getId())
                .midiFilePath(midiUrl)
                .mappings(mappingDataList)
                .targetInstrumentId(version.getInstrumentId())
                .targetInstrumentName(instrumentName)
                .targetMidiProgram(version.getInstrumentId())
                .minNote(version.getMinNote())
                .maxNote(version.getMaxNote())
                .modelType(modelType)
                .genre(version.getGenre() != null ? version.getGenre().name() : "CLASSICAL")
                .temperature(version.getTemperature() != null ? version.getTemperature() : 1.0)
                .callbackUrl(callbackBaseUrl + "/internal/callback/arrange")
                .callbackSecret(callbackSecret)
                .build();

        // 3. Redis Stream에 편곡 요청 발행 (AI Worker가 XREADGROUP으로 소비)
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            redisTemplate.opsForStream().add(STREAM_KEY, Map.of("data", requestJson));
            log.info("AI 편곡 요청 스트림 등록 완료: project={}, version={}",
                    project.getId(), version.getId());
        } catch (Exception e) {
            log.error("Redis 큐 발행 실패: project={}, version={}, error={}",
                    project.getId(), version.getId(), e.getMessage());
            handleAiRequestFailure(project.getId(), version.getId());
        }
    }

    /**
     * AI 요청 실패 처리 — Redis 큐 발행 실패 또는 콜백 처리 오류 시 호출됩니다.
     */
    @Transactional
    public void handleAiRequestFailure(Long projectId, Long versionId) {
        versionRepository.findById(versionId).ifPresent(version -> {
            version.updateStatus(ProjectVersion.VersionStatus.FAILED);
            version.updateProgress(0);
            versionRepository.save(version);
        });

        // Redis Pub/Sub로 실패 이벤트 브로드캐스트 (모든 Pod에 전달)
        // → deliverSseEventLocally()에서 status=failed 감지 시 emitter 자동 종료
        publishSseEvent(ProgressEvent.builder()
                .projectId(projectId)
                .versionId(versionId)
                .status("failed")
                .progress(0)
                .build());
    }

    // ══════════════════════════════════════════════════════
    // AI 서버 콜백 처리
    // ══════════════════════════════════════════════════════

    /**
     * AI 서버의 JSON 콜백을 처리합니다 — 진행률 업데이트 및 실패 알림용.
     * (완료 콜백은 handleCallbackWithFile()에서 MIDI 파일과 함께 처리됩니다)
     *
     * @param payload AI 서버가 보낸 콜백 데이터 (JSON)
     */
    @Transactional
    public void handleCallback(AiCallbackPayload payload) {
        // 0. payload 유효성 검증 — 잘못된 콜백 방어
        if (payload.getVersionId() == null) {
            log.error("콜백 payload에 versionId가 없습니다. 무시합니다.");
            return;
        }
        if (payload.getProjectId() == null) {
            log.warn("콜백 payload에 projectId가 없습니다: versionId={}", payload.getVersionId());
        }

        log.info("AI 콜백 수신 (JSON): project={}, version={}, status={}",
                payload.getProjectId(), payload.getVersionId(), payload.getStatus());

        // 1. 상태값 파싱 — null이거나 알 수 없는 값이면 FAILED로 안전하게 폴백
        ProjectVersion.VersionStatus parsedStatus;
        try {
            if (payload.getStatus() == null) {
                log.warn("콜백 status가 null입니다: versionId={}", payload.getVersionId());
                parsedStatus = ProjectVersion.VersionStatus.FAILED;
            } else {
                parsedStatus = ProjectVersion.VersionStatus.valueOf(payload.getStatus().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 상태값: {}", payload.getStatus());
            parsedStatus = ProjectVersion.VersionStatus.FAILED;
        }
        final ProjectVersion.VersionStatus status = parsedStatus;

        // 2. DB 업데이트 — 상태, 진행률
        versionRepository.findById(payload.getVersionId()).ifPresent(version -> {
            version.updateStatus(status);
            if (payload.getProgress() != null) {
                version.updateProgress(payload.getProgress());
            }
            versionRepository.save(version);
        });

        // 3. Redis Pub/Sub로 SSE 이벤트 브로드캐스트 (모든 Pod에 전달)
        publishSseEvent(ProgressEvent.builder()
                .projectId(payload.getProjectId())
                .versionId(payload.getVersionId())
                .status(status.name().toLowerCase())
                .progress(payload.getProgress() != null ? payload.getProgress() : 0)
                .build());

        // 4. 실패 시 SSE 연결 종료는 deliverSseEventLocally()가 자동 처리
    }

    /**
     * AI 서버의 Multipart 완료 콜백을 처리합니다.
     * 온프레미스 AI 서버에서 결과를 안정적으로 전달하기 위해,
     * MIDI 파일을 콜백과 함께 직접 수신합니다.
     *
     * <p>
     * <b>처리 흐름:</b>
     * </p>
     * <ol>
     * <li>MIDI 바이트를 Supabase Storage에 직접 업로드</li>
     * <li>Converter 서비스로 XML/PDF 변환 후 업로드</li>
     * <li>DB 상태 COMPLETE + 결과 경로 업데이트</li>
     * <li>SSE 완료 이벤트 전송 + 연결 종료</li>
     * </ol>
     *
     * @param payload   metadata JSON에서 파싱된 콜백 페이로드
     * @param midiBytes AI 서버가 전송한 편곡 결과 MIDI 파일 바이트
     */
    @Transactional
    public void handleCallbackWithFile(AiCallbackPayload payload, byte[] midiBytes) {
        // 0. payload 유효성 검증
        if (payload.getVersionId() == null || payload.getProjectId() == null) {
            log.error("Multipart 콜백 payload 불완전: projectId={}, versionId={}",
                    payload.getProjectId(), payload.getVersionId());
            return;
        }

        log.info("AI 완료 콜백 수신 (multipart): project={}, version={}, midiSize={}",
                payload.getProjectId(), payload.getVersionId(), midiBytes.length);

        // 1. MIDI 파일을 Supabase에 직접 업로드
        String midiStoragePath = payload.getProjectId() + "/" + payload.getVersionId() + "/result.mid";
        storageService.upload(
                SupabaseStorageService.BUCKET_RESULTS,
                midiStoragePath, midiBytes, "audio/midi");
        log.info("MIDI 결과물 Supabase 업로드 완료: {}", midiStoragePath);

        // 2. XML/PDF 변환
        String xmlStoragePath = null;
        String pdfStoragePath = null;
        try {
            xmlStoragePath = convertAndUpload(midiBytes,
                    payload.getProjectId(), payload.getVersionId(),
                    "result.musicxml", "application/xml",
                    converterService::midiToMusicXml);

            pdfStoragePath = convertAndUpload(midiBytes,
                    payload.getProjectId(), payload.getVersionId(),
                    "result.pdf", "application/pdf",
                    converterService::midiToPdf);
        } catch (Exception e) {
            log.error("XML/PDF 변환 중 오류 (MIDI는 저장됨): {}", e.getMessage());
        }

        // 3. DB 업데이트 — COMPLETE + 결과 파일 경로
        final String finalXml = xmlStoragePath;
        final String finalPdf = pdfStoragePath;

        versionRepository.findById(payload.getVersionId()).ifPresent(version -> {
            version.updateStatus(ProjectVersion.VersionStatus.COMPLETE);
            version.updateProgress(100);
            version.updateResultPaths(midiStoragePath, finalXml, finalPdf);
            versionRepository.save(version);
        });

        // 4. Redis Pub/Sub로 완료 이벤트 브로드캐스트
        // → deliverSseEventLocally()에서 status=complete 감지 시 emitter 자동 종료
        publishSseEvent(ProgressEvent.builder()
                .projectId(payload.getProjectId())
                .versionId(payload.getVersionId())
                .status("complete")
                .progress(100)
                .build());
    }

    /**
     * MIDI 데이터를 변환하고 Supabase Storage에 업로드합니다.
     * 변환 실패 시 null을 반환합니다 (비치명적 실패).
     */
    private String convertAndUpload(byte[] midiBytes, Long projectId, Long versionId,
            String filename, String contentType,
            java.util.function.Function<byte[], byte[]> converter) {
        try {
            byte[] converted = converter.apply(midiBytes);
            if (converted == null) {
                log.warn("변환 실패 (null 반환): {}", filename);
                return null;
            }

            String storagePath = projectId + "/" + versionId + "/" + filename;
            storageService.upload(
                    SupabaseStorageService.BUCKET_RESULTS,
                    storagePath, converted, contentType);

            log.info("변환 + 업로드 완료: {}", storagePath);
            return storagePath;
        } catch (Exception e) {
            log.error("변환/업로드 실패 ({}): {}", filename, e.getMessage());
            return null;
        }
    }

    /** 콜백 인증에 사용할 시크릿 — InternalCallbackController에서 검증합니다. */
    public String getCallbackSecret() {
        return callbackSecret;
    }

    // ══════════════════════════════════════════════════════
    // SSE (Server-Sent Events) 관리
    // ══════════════════════════════════════════════════════

    /**
     * SSE 구독 — 클라이언트가 실시간 진행률을 받기 위해 연결합니다.
     *
     * <p>
     * <b>SSE(Server-Sent Events)란?</b>
     * </p>
     * HTTP 연결을 열어두고 서버가 클라이언트에게 지속적으로 데이터를 "푸시"하는 기술입니다.
     * WebSocket과 달리 단방향(서버→클라이언트)이고, HTTP 표준이므로 별도의 프로토콜이 필요 없습니다.
     *
     * <p>
     * <b>생명주기:</b>
     * </p>
     * 
     * <pre>
     * Client GET /{projectId}/{versionId}/status
     *   → SseEmitter 생성 (10분 타임아웃)
     *   → 초기 상태 전송 (현재 진행률)
     *   → AI 콜백 수신 시마다 이벤트 전송
     *   → COMPLETE/FAILED 이벤트 전송 후 클라이언트가 eventSource.close()로 종료
     *   → 클라이언트 미종료 시 타임아웃(10분) + GC 스케줄러가 자동 정리
     * </pre>
     *
     * @param projectId 프로젝트 ID
     * @param versionId 버전 ID
     * @return SSE 연결을 나타내는 SseEmitter 객체
     */
    public SseEmitter subscribe(Long projectId, Long versionId) {
        // 1. 새 SSE emitter 생성 (10분 타임아웃)
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 복합 키: "projectId:versionId"
        String key = emitterKey(projectId, versionId);

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitterCreatedAt.putIfAbsent(key, System.currentTimeMillis());

        // 2. 연결 종료 시 자동 정리 콜백 등록
        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(key);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(key);
                    emitterCreatedAt.remove(key);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 3. 초기 상태 전송 — 이미 COMPLETE면 즉시 종료 (Pod 사망 복구 핵심)
        versionRepository.findById(versionId)
                .ifPresent(version -> sendInitialStatus(emitter, projectId, versionId, version));

        return emitter;
    }

    // ── 내부 SSE 헬퍼 메서드 ──

    /** 구독 직후 현재 상태를 즉시 전송합니다. 이미 완료된 버전이면 바로 SSE를 닫습니다. */
    private void sendInitialStatus(SseEmitter emitter, Long projectId, Long versionId,
            ProjectVersion version) {
        try {
            ProgressEvent event = ProgressEvent.builder()
                    .projectId(projectId)
                    .versionId(versionId)
                    .status(version.getStatus().name().toLowerCase())
                    .progress(version.getProgress())
                    .build();

            String message = toStatusMessage(version.getStatus());
            ApiResponse<ProgressEvent> response = ApiResponse.success(message, event);

            // SSE 이벤트: "progress"라는 이름으로 JSON 데이터를 전송
            emitter.send(SseEmitter.event().name("progress").data(response));

            // FIN 패킷 레이스 컨디션을 막기 위해 백엔드에서 강제로 연결을 먼저 끊지 않음
            // 프론트엔드가 이벤트를 받고 eventSource.close()를 호출하면 onCompletion 훅이 발동하여 정리됨
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    // ── Redis Pub/Sub를 통한 SSE 브로드캐스트 ──

    /**
     * Redis Pub/Sub로 SSE 이벤트를 전체 Pod에 브로드캐스트합니다.
     * 콜백 핸들러에서 DB 업데이트 후 호출됩니다.
     *
     * <pre>
     * 콜백 수신 → DB 업데이트 → publishSseEvent() → Redis PUBLISH
     *   → 모든 Pod가 SUBSCRIBE 수신 → deliverSseEventLocally()
     * </pre>
     */
    private void publishSseEvent(ProgressEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishSseEvent(event);
                }
            });
        } else {
            doPublishSseEvent(event);
        }
    }

    private void doPublishSseEvent(ProgressEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(SSE_CHANNEL, json);
        } catch (Exception e) {
            log.error("Redis SSE publish 실패, 로컬 폴백: {}", e.getMessage());
            // 폴백: Redis 장애 시 현재 Pod의 로컬 emitter라도 시도
            deliverSseEventLocally(event);
        }
    }

    /**
     * 로컬 Pod의 인메모리 emitter에 SSE 이벤트를 전달합니다.
     * {@link com.tutti.server.infra.redis.SseRedisSubscriber}에서 호출됩니다.
     *
     * <p>
     * 이 Pod에 해당 emitter가 없으면 아무 일도 하지 않습니다 (정상 동작).
     * </p>
     */
    public void deliverSseEventLocally(ProgressEvent event) {
        String key = emitterKey(event.getProjectId(), event.getVersionId());
        sendSseEvent(key, event);

        // 프론트엔드가 마지막 패킷(완료/실패)을 완전히 수신하기 전 서버가 
        // 소켓을 닫아버리는 문제(Truncation)를 방지하기 위해 강제 종료 로직 제거.
        // 연결은 프론트엔드 측에서 eventSource.close()를 호출하거나 SseEmitter의 기본 타임아웃에 의해 종료됨.
    }

    /** 특정 복합 키에 연결된 모든 SSE 클라이언트에게 이벤트를 전송합니다. */
    private void sendSseEvent(String key, ProgressEvent event) {
        List<SseEmitter> emitterList = emitters.get(key);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        String message = toStatusMessage(
                ProjectVersion.VersionStatus.valueOf(event.getStatus().toUpperCase()));
        ApiResponse<ProgressEvent> response = ApiResponse.success(message, event);

        // 전송 실패한 emitter(연결 끊김)는 별도 리스트에 모아서 일괄 제거
        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(response));
            } catch (IOException e) {
                log.warn("SSE 이벤트 전송 실패: key={}", key);
                deadEmitters.add(emitter);
            }
        }
        emitterList.removeAll(deadEmitters);
        if (emitterList.isEmpty()) {
            emitters.remove(key);
            emitterCreatedAt.remove(key);
        }
    }



    /** VersionStatus를 사용자에게 친절한 한글 메시지로 변환합니다. */
    private String toStatusMessage(ProjectVersion.VersionStatus status) {
        return switch (status) {
            case PENDING -> "대기 중...";
            case PROCESSING -> "생성 중...";
            case COMPLETE -> "완료되었습니다.";
            case FAILED -> "처리에 실패했습니다.";
        };
    }

    /**
     * 카테고리 representative_program을 AI 모델 타입 문자열로 변환합니다.
     * 예: 40 → "solo_string", 61 → "brass"
     */
    private String resolveModelType(Integer instrumentId) {
        if (instrumentId == null) return "default";
        return categoryRepository.findById(instrumentId)
                .map(InstrumentCategory::getName)
                .map(name -> name.toLowerCase().replace(" ", "_"))
                .orElse("default");
    }

    /**
     * 개별 악기 ID → 소속 카테고리의 representative_program으로 변환합니다.
     * 이미 카테고리 대표값이면 그대로 반환합니다.
     *
     * <p>
     * 예: 41(Viola) → 40(Solo String), 40(Violin) → 40
     * </p>
     */
    private Integer resolveCategoryProgram(Integer instrumentId) {
        if (instrumentId == null) return null;
        // 이미 카테고리 대표값이면 그대로 반환
        if (categoryRepository.existsById(instrumentId)) {
            return instrumentId;
        }
        // 개별 악기 → 카테고리 대표값
        return instrumentRepository.findById(instrumentId)
                .map(i -> i.getCategory().getRepresentativeProgram())
                .orElse(instrumentId);
    }

    /**
     * 악기 ID → 실제 악기 이름을 반환합니다.
     * 개별 악기 ID면 해당 악기의 이름("Viola"), 카테고리 대표값이면 카테고리명("Solo String")을 반환.
     */
    private String resolveInstrumentName(Integer instrumentId) {
        if (instrumentId == null) return null;
        // 먼저 개별 악기에서 찾기
        return instrumentRepository.findById(instrumentId)
                .map(Instrument::getName)
                .orElseGet(() -> categoryRepository.findById(instrumentId)
                        .map(InstrumentCategory::getName)
                        .orElse("Unknown"));
    }
}
