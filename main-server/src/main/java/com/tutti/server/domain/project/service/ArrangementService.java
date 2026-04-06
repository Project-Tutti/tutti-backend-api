package com.tutti.server.domain.project.service;

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
import com.tutti.server.domain.instrument.entity.InstrumentCategory;
import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 편곡 서비스 — FastAPI AI 서버와의 비동기 통신 및 SSE 진행률 스트리밍을 관리합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * ProjectService → <b>ArrangementService</b> ──(WebClient)──→ FastAPI AI 서버 (비동기)
 *                                           ←──(콜백)────← FastAPI AI 서버
 *                        │
 *                  InternalCallbackController (콜백 수신)
 *                        │
 *               Client ←─(SSE)── 이 서비스의 emitters
 * </pre>
 *
 * <h3>핵심 동작 흐름</h3>
 * <ol>
 * <li>{@code requestArrangement()} — AI 서버에 편곡 요청을 비동기(WebClient)로 전송</li>
 * <li>{@code subscribe()} — 클라이언트가 SSE 연결을 열고 실시간 진행률을 수신</li>
 * <li>{@code handleCallback()} — AI 서버가 진행 상황을 HTTP 콜백으로 전달</li>
 * <li>콜백 수신 → DB 상태 업데이트 + SSE 이벤트 전송 → (완료/실패 시) SSE 종료</li>
 * </ol>
 *
 * <h3>메모리 안전성</h3>
 * SSE emitter는 {@link ConcurrentHashMap}에 저장되며, {@code @Scheduled}로
 * 만료된 emitter를 주기적으로 정리하여 메모리 누수를 방지합니다.
 *
 * @see com.tutti.server.infra.ai.InternalCallbackController
 * @see com.tutti.server.global.config.WebClientConfig
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
    private final WebClient aiWebClient;
    private final ProjectVersionRepository versionRepository;
    private final VersionMappingRepository mappingRepository;
    private final SupabaseStorageService storageService;
    private final ConverterService converterService;
    private final InstrumentCategoryRepository categoryRepository;

    // @Value: application.yml의 설정값을 주입받습니다.
    // ${...} 안의 값은 환경변수나 설정 파일에서 자동으로 치환됩니다.
    @Value("${ai.server.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${ai.server.callback-secret:default-callback-secret}")
    private String callbackSecret;

    // ── SSE Emitter 저장소 ──
    // ConcurrentHashMap: 동시에 여러 스레드가 접근해도 안전한 Map.
    // 여러 클라이언트가 동시에 SSE를 구독/해제할 수 있으므로 동시성 안전이 필수입니다.
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    /** emitter 생성 시각 — 만료 정리 스케줄러에서 사용합니다. */
    private final Map<Long, Long> emitterCreatedAt = new ConcurrentHashMap<>();

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
                Long versionId = entry.getKey();
                List<SseEmitter> list = emitters.remove(versionId);
                if (list != null) {
                    list.forEach(emitter -> {
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                    });
                }
                log.debug("만료 SSE emitter 정리: versionId={}", versionId);
                return true;
            }
            return false;
        });
    }

    // ══════════════════════════════════════════════════════
    // AI 서버 비동기 호출
    // ══════════════════════════════════════════════════════

    /**
     * AI 서버에 편곡 요청을 비동기로 전송합니다.
     *
     * <p>
     * <b>비동기로 호출하는 이유:</b>
     * </p>
     * AI 편곡은 3~5분이 걸리므로, HTTP 응답을 기다리지 않고 즉시 반환합니다.
     * AI 서버는 작업이 완료되면 {@code callbackUrl}로 결과를 POST합니다.
     *
     * <p>
     * <b>데이터 흐름:</b>
     * </p>
     * 
     * <pre>
     * 이 메서드 →(WebClient POST)→ FastAPI /api/v1/arrange
     *   → AI 서버가 작업 완료 후 →(POST)→ /internal/callback/arrange
     *     → handleCallback() → DB 저장 + SSE 전송
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
        String modelType = resolveModelType(version.getInstrumentId());

        AiArrangeRequest request = AiArrangeRequest.builder()
                .projectId(project.getId())
                .versionId(version.getId())
                .midiFilePath(midiUrl)
                .mappings(mappingDataList)
                .targetInstrumentId(version.getInstrumentId())
                .minNote(version.getMinNote())
                .maxNote(version.getMaxNote())
                .modelType(modelType)
                .genre(version.getGenre() != null ? version.getGenre().name() : "CLASSICAL")
                .temperature(version.getTemperature() != null ? version.getTemperature() : 1.0)
                .callbackUrl(callbackBaseUrl + "/internal/callback/arrange")
                .callbackSecret(callbackSecret)
                .build();

        // 3. 비동기 전송 — subscribe()로 "발사 후 잊기(fire-and-forget)"
        aiWebClient.post()
                .uri("/api/v1/arrange")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> {
                    log.info("AI 서버 편곡 요청 전송 완료: project={}, version={}",
                            project.getId(), version.getId());
                })
                .doOnError(error -> {
                    log.error("AI 서버 편곡 요청 실패: project={}, version={}, error={}",
                            project.getId(), version.getId(), error.getMessage());
                    handleAiRequestFailure(version.getId());
                })
                .subscribe(); // 비동기 실행 — 응답을 기다리지 않음
    }

    /**
     * AI 서버 요청 자체가 실패한 경우의 처리.
     * 네트워크 오류, 타임아웃, AI 서버 다운 등의 상황에서 호출됩니다.
     */
    @Transactional
    public void handleAiRequestFailure(Long versionId) {
        versionRepository.findById(versionId).ifPresent(version -> {
            version.updateStatus(ProjectVersion.VersionStatus.FAILED);
            version.updateProgress(0);
            versionRepository.save(version);
        });

        // SSE로 실패 상태를 연결된 클라이언트에게 전송
        sendSseEvent(versionId, ProgressEvent.builder()
                .versionId(versionId)
                .status("failed")
                .progress(0)
                .build());
        completeSseEmitters(versionId);
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
        log.info("AI 콜백 수신 (JSON): project={}, version={}, status={}",
                payload.getProjectId(), payload.getVersionId(), payload.getStatus());

        // 1. 상태값 파싱 — 알 수 없는 값이면 FAILED로 안전하게 폴백
        ProjectVersion.VersionStatus parsedStatus;
        try {
            parsedStatus = ProjectVersion.VersionStatus.valueOf(payload.getStatus().toUpperCase());
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

        // 3. SSE 이벤트 전송
        sendSseEvent(payload.getVersionId(), ProgressEvent.builder()
                .projectId(payload.getProjectId())
                .versionId(payload.getVersionId())
                .status(status.name().toLowerCase())
                .progress(payload.getProgress() != null ? payload.getProgress() : 0)
                .build());

        // 4. 실패 시 SSE 연결 종료
        if (status == ProjectVersion.VersionStatus.FAILED) {
            completeSseEmitters(payload.getVersionId());
        }
    }

    /**
     * AI 서버의 Multipart 완료 콜백을 처리합니다.
     * KEDA 0-Scaling 환경에서 AI 파드가 즉시 삭제될 수 있으므로,
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

        // 4. SSE 완료 이벤트 전송 + 연결 종료
        sendSseEvent(payload.getVersionId(), ProgressEvent.builder()
                .projectId(payload.getProjectId())
                .versionId(payload.getVersionId())
                .status("complete")
                .progress(100)
                .build());
        completeSseEmitters(payload.getVersionId());
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
     *   → COMPLETE/FAILED 시 emitter.complete()로 종료
     * </pre>
     *
     * @param projectId 프로젝트 ID
     * @param versionId 버전 ID
     * @return SSE 연결을 나타내는 SseEmitter 객체
     */
    public SseEmitter subscribe(Long projectId, Long versionId) {
        // 1. 새 SSE emitter 생성 (10분 타임아웃)
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // CopyOnWriteArrayList: 읽기가 많고 쓰기가 적은 경우에 최적화된 스레드 안전 리스트
        emitters.computeIfAbsent(versionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitterCreatedAt.putIfAbsent(versionId, System.currentTimeMillis());

        // 2. 연결 종료 시 자동 정리 콜백 등록
        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(versionId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(versionId);
                    emitterCreatedAt.remove(versionId);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 3. 초기 상태 전송 — 이미 COMPLETE면 즉시 종료
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

            if (version.isComplete()
                    || version.getStatus() == ProjectVersion.VersionStatus.FAILED) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** 특정 버전에 연결된 모든 SSE 클라이언트에게 이벤트를 전송합니다. */
    private void sendSseEvent(Long versionId, ProgressEvent event) {
        List<SseEmitter> emitterList = emitters.get(versionId);
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
                log.warn("SSE 이벤트 전송 실패: versionId={}", versionId);
                deadEmitters.add(emitter);
            }
        }
        emitterList.removeAll(deadEmitters);
        if (emitterList.isEmpty()) {
            emitters.remove(versionId);
            emitterCreatedAt.remove(versionId);
        }
    }

    /** 완료/실패 시 모든 SSE 연결을 종료합니다. */
    private void completeSseEmitters(Long versionId) {
        List<SseEmitter> emitterList = emitters.remove(versionId);
        emitterCreatedAt.remove(versionId);
        if (emitterList != null) {
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("SSE emitter 종료 중 오류: {}", e.getMessage());
                }
            }
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
}
