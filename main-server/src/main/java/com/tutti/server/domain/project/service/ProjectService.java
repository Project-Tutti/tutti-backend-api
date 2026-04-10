package com.tutti.server.domain.project.service;

import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import com.tutti.server.domain.project.dto.request.*;
import com.tutti.server.domain.project.dto.response.*;
import com.tutti.server.domain.project.entity.*;
import com.tutti.server.domain.project.repository.*;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import com.tutti.server.infra.storage.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 관리 서비스 — MIDI 편곡 프로젝트의 전체 라이프사이클을 관리합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client] → ProjectController → <b>ProjectService</b> → Repository(DB)
 *                                                      → ArrangementService(AI 서버)
 * </pre>
 *
 * <h3>핵심 책임</h3>
 * <ol>
 * <li><b>프로젝트 CRUD:</b> 생성, 조회, 이름 수정, Soft Delete</li>
 * <li><b>버전 관리:</b> 재생성(새 버전 생성), 이름 수정, 삭제</li>
 * <li><b>파일 I/O:</b> MIDI 파일 업로드/저장, 결과 파일 다운로드</li>
 * <li><b>소유권 검증:</b> 모든 API에서 요청자가 프로젝트 소유자인지 확인</li>
 * </ol>
 *
 * <h3>주요 의존성</h3>
 * <ul>
 * <li>{@link ArrangementService} — AI 서버에 편곡 요청을 비동기로 전송</li>
 * <li>5개 Repository — Project, ProjectVersion, ProjectTrack, VersionMapping,
 * Profile</li>
 * </ul>
 *
 * <h3>데이터 삭제 정책</h3>
 * 프로젝트와 버전은 모두 <b>Soft Delete</b>를 사용합니다.
 * Repository 쿼리에서 {@code deletedAt IS NULL} 조건으로 필터링되며,
 * 실제 데이터 정리는 별도의 배치 작업에서 수행합니다.
 *
 * @see ArrangementService
 * @see com.tutti.server.domain.project.controller.ProjectController
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository versionRepository;
    private final ProjectTrackRepository trackRepository;
    private final VersionMappingRepository mappingRepository;
    private final ProfileRepository profileRepository;
    private final InstrumentCategoryRepository categoryRepository;
    private final ArrangementService arrangementService;
    private final SupabaseStorageService storageService;

    // ══════════════════════════════════════
    // 3.1 프로젝트 생성
    // ══════════════════════════════════════

    /**
     * 새 프로젝트를 생성하고 AI 편곡을 시작합니다.
     *
     * <p>
     * <b>데이터 흐름:</b>
     * </p>
     * 
     * <pre>
     * Client (MIDI 파일 + 매핑 정보)
     *   → 1. 사용자 검증
     *   → 2. MIDI 파일 형식/크기 검증
     *   → 3. 파일 저장 (로컬 디스크)
     *   → 4. Project 엔티티 생성 → DB 저장
     *   → 5. 트랙 정보 저장
     *   → 6. Version 생성 + 매핑 저장
     *   → 7. ArrangementService 비동기 호출 → AI 서버
     *   → 8. ProjectCreateResponse 반환
     * </pre>
     *
     * @param userId  요청자 사용자 ID (JWT에서 추출)
     * @param file    업로드된 MIDI 파일
     * @param request 프로젝트명, 트랙/매핑 정보를 담은 요청 DTO
     * @return 생성된 프로젝트 ID, 버전 ID, 현재 상태
     * @throws BusinessException USER_NOT_FOUND — 탈퇴한 사용자
     * @throws BusinessException INVALID_FILE_FORMAT — .mid/.midi 확장자가 아닌 파일
     * @throws BusinessException FILE_TOO_LARGE — 10MB 초과
     */
    @Transactional
    public ProjectCreateResponse createProject(UUID userId, MultipartFile file, ProjectCreateRequest request) {
        // 1. 사용자 검증 — 탈퇴한 사용자는 프로젝트를 생성할 수 없음
        Profile profile = findActiveProfile(userId);

        // 2. MIDI 파일 유효성 검증 — 형식과 크기 확인
        validateMidiFile(file);

        // 2-1. 장르 유효성 검증
        Genre genre = resolveGenre(request.getGenre());

        // 3. 파일을 서버 로컬에 저장하고 경로를 반환
        String midiFilePath = saveMidiFile(userId, file);

        // 4. Project 엔티티 생성 → DB 저장
        Project project = Project.builder()
                .user(profile)
                .name(request.getName())
                .originalFileName(file.getOriginalFilename())
                .midiFilePath(midiFilePath)
                .build();
        projectRepository.save(project);

        // 5. 원본 MIDI 트랙 정보 저장 (예: 0번 트랙=피아노, 1번 트랙=기타)
        saveTracks(project, request.getTracks());

        // 6. 첫 번째 편곡 버전 생성
        String versionName = (request.getVersionName() != null && !request.getVersionName().isBlank())
                ? request.getVersionName()
                : "Ver 1";

        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .name(versionName)
                .status(ProjectVersion.VersionStatus.PENDING)
                .instrumentId(request.getInstrumentId())
                .minNote(request.getMinNote())
                .maxNote(request.getMaxNote())
                .genre(genre)
                .temperature(request.getTemperature() != null ? request.getTemperature() : 1.0)
                .build();
        versionRepository.save(version);

        // 7. 트랙별 악기 매핑 저장 (예: 0번 트랙 → 바이올린으로 편곡)
        saveMappings(version, request.getMappings());

        // 8. 상태를 PROCESSING으로 전환하고 AI 서버에 비동기 편곡 요청
        version.updateStatus(ProjectVersion.VersionStatus.PROCESSING);
        versionRepository.save(version);
        arrangementService.requestArrangement(project, version);

        return ProjectCreateResponse.builder()
                .projectId(project.getId())
                .versionId(version.getId())
                .status(version.getStatus().name().toLowerCase())
                .build();
    }

    // ══════════════════════════════════════
    // 3.2 프로젝트 조회
    // ══════════════════════════════════════

    /**
     * 프로젝트 상세 조회 — 버전 히스토리와 매핑 정보를 포함합니다.
     *
     * <p>
     * <b>N+1 방지:</b> {@code findByProjectIdWithMappings()}가
     * JPQL Fetch Join을 사용하여 버전과 매핑을 단일 쿼리로 조회합니다.
     * </p>
     *
     * @throws BusinessException PROJECT_NOT_FOUND, ACCESS_DENIED
     */
    public ProjectDetailResponse getProject(UUID userId, Long projectId) {
        Project project = findProjectWithOwnership(userId, projectId);

        // N+1 방지: fetch join으로 버전 + 매핑을 한번에 로드
        List<ProjectVersion> versions = versionRepository.findByProjectIdWithMappings(projectId);
        List<VersionResponse> versionResponses = versions.stream()
                .map(VersionResponse::from)
                .toList();

        return ProjectDetailResponse.from(project, versionResponses);
    }

    // ══════════════════════════════════════
    // 3.3 트랙 정보 조회
    // ══════════════════════════════════════

    /**
     * 프로젝트의 원본 MIDI 트랙 목록 조회.
     * 사용자가 "재생성" 시 어떤 트랙을 어떤 악기로 매핑할지 선택할 때 사용됩니다.
     */
    public TrackInfoResponse getTracks(UUID userId, Long projectId) {
        findProjectWithOwnership(userId, projectId);

        List<ProjectTrack> tracks = trackRepository.findByProjectId(projectId);
        List<TrackInfoResponse.TrackItem> items = tracks.stream()
                .map(TrackInfoResponse.TrackItem::from)
                .toList();

        return TrackInfoResponse.builder().tracks(items).build();
    }

    // ══════════════════════════════════════
    // 3.5 재생성 (새 버전 생성)
    // ══════════════════════════════════════

    /**
     * 기존 프로젝트에서 새로운 편곡 버전을 생성합니다.
     * 동일한 MIDI 파일에 다른 악기 매핑을 적용하여 다양한 편곡 결과를 만들 수 있습니다.
     *
     * <p>
     * 버전 이름을 지정하지 않으면 "Ver N" 형태로 자동 생성됩니다.
     * 여기서 N은 삭제되지 않은 버전의 개수 + 1입니다.
     * </p>
     */
    @Transactional
    public ProjectCreateResponse regenerate(UUID userId, Long projectId, RegenerateRequest request) {
        Project project = findProjectWithOwnership(userId, projectId);

        // 장르 유효성 검증 (null이면 fallback에서 처리)
        Genre genre = request.getGenre() != null ? resolveGenre(request.getGenre()) : null;

        // Soft Delete되지 않은 버전의 수를 기반으로 자동 이름 생성
        long versionCount = versionRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        String versionName = (request.getVersionName() != null && !request.getVersionName().isBlank())
                ? request.getVersionName()
                : "Ver " + (versionCount + 1);

        // Fallback: 직전 버전에서 생성 설정을 가져옴
        ProjectVersion latestVersion = versionRepository
                .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
                .orElse(null);

        Integer effectiveInstrumentId = request.getInstrumentId();
        Integer effectiveMinNote = request.getMinNote();
        Integer effectiveMaxNote = request.getMaxNote();
        Genre effectiveGenre = genre;
        Double effectiveTemperature = request.getTemperature();

        if (latestVersion != null) {
            if (effectiveInstrumentId == null) effectiveInstrumentId = latestVersion.getInstrumentId();
            if (effectiveMinNote == null) effectiveMinNote = latestVersion.getMinNote();
            if (effectiveMaxNote == null) effectiveMaxNote = latestVersion.getMaxNote();
            if (effectiveGenre == null) effectiveGenre = latestVersion.getGenre();
            if (effectiveTemperature == null) effectiveTemperature = latestVersion.getTemperature();
        }

        // fallback 후에도 null이면 기본값 사용
        if (effectiveGenre == null) effectiveGenre = Genre.CLASSICAL;
        if (effectiveTemperature == null) effectiveTemperature = 1.0;

        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .name(versionName)
                .status(ProjectVersion.VersionStatus.PENDING)
                .instrumentId(effectiveInstrumentId)
                .minNote(effectiveMinNote)
                .maxNote(effectiveMaxNote)
                .genre(effectiveGenre)
                .temperature(effectiveTemperature)
                .build();
        versionRepository.save(version);

        saveMappings(version, request.getMappings());

        // PROCESSING으로 전환 → AI 서버에 비동기 요청
        version.updateStatus(ProjectVersion.VersionStatus.PROCESSING);
        versionRepository.save(version);
        arrangementService.requestArrangement(project, version);

        return ProjectCreateResponse.builder()
                .projectId(project.getId())
                .versionId(version.getId())
                .status(version.getStatus().name().toLowerCase())
                .build();
    }

    // ══════════════════════════════════════
    // 3.6 악보 데이터 조회
    // ══════════════════════════════════════

    /**
     * MusicXML 악보 데이터를 반환합니다 — 프론트엔드 악보 뷰어에서 렌더링용.
     *
     * @throws BusinessException PROCESSING_NOT_COMPLETE — 아직 편곡이 완료되지 않음
     * @throws BusinessException RESOURCE_NOT_FOUND — 결과 파일이 존재하지 않음
     */
    public Resource getScore(UUID userId, Long projectId, Long versionId) {
        findProjectWithOwnership(userId, projectId);
        ProjectVersion version = findVersion(projectId, versionId);

        // 편곡이 완료되지 않은 버전은 악보를 조회할 수 없음
        if (!version.isComplete()) {
            throw new BusinessException(ErrorCode.PROCESSING_NOT_COMPLETE);
        }

        if (version.getResultXmlPath() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Resource resource = storageService.downloadAsResource(
                SupabaseStorageService.BUCKET_RESULTS, version.getResultXmlPath());
        if (resource == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return resource;
    }

    // ══════════════════════════════════════
    // 3.7 파일 다운로드
    // ══════════════════════════════════════

    /**
     * 편곡 결과 파일의 다운로드 링크(Signed URL)를 생성합니다.
     *
     * <p>
     * Supabase Storage의 Signed URL을 사용하여 클라이언트가 직접 다운로드할 수 있는
     * 시간 제한 링크를 발급합니다. 백엔드가 파일을 중계하지 않으므로
     * 서버 메모리/대역폭 사용이 줄어듭니다.
     * </p>
     *
     * @param type "midi", "xml", "pdf" 중 하나
     * @throws BusinessException INVALID_FILE_TYPE — 지원하지 않는 타입
     * @throws BusinessException PROCESSING_NOT_COMPLETE — 편곡 미완료
     * @throws BusinessException RESOURCE_NOT_FOUND — 파일 미존재
     */
    public DownloadLinkResponse downloadFile(UUID userId, Long projectId, Long versionId, String type) {
        // 1. 파일 타입 유효성 검증
        DownloadFileType fileType = DownloadFileType.fromString(type);
        if (fileType == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        // 2. 소유권 + 버전 검증
        findProjectWithOwnership(userId, projectId);
        ProjectVersion version = findVersion(projectId, versionId);

        // 3. 처리 완료 여부 확인
        if (!version.isComplete()) {
            throw new BusinessException(ErrorCode.PROCESSING_NOT_COMPLETE);
        }

        // 4. 도메인 메서드로 파일 경로 조회
        String resultPath = version.getResultPathByType(type);
        if (resultPath == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 5. Supabase Signed URL 생성 (유효 시간: 5분)
        String signedUrl = storageService.createSignedUrl(
                SupabaseStorageService.BUCKET_RESULTS, resultPath, 300);

        return DownloadLinkResponse.builder()
                .downloadLink(signedUrl)
                .build();
    }

    // ══════════════════════════════════════
    // 3.8 프로젝트 이름 수정
    // ══════════════════════════════════════

    /** 프로젝트 이름 변경. 소유자만 변경할 수 있습니다. */
    @Transactional
    public ProjectRenameResponse renameProject(UUID userId, Long projectId, ProjectRenameRequest request) {
        Project project = findProjectWithOwnership(userId, projectId);
        project.rename(request.getName());

        return ProjectRenameResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    // ══════════════════════════════════════
    // 3.9 프로젝트 삭제 (Soft Delete)
    // ══════════════════════════════════════

    /**
     * 프로젝트 삭제 — Soft Delete.
     * {@code project.softDelete()}를 호출하면 {@code deletedAt} 필드에 현재 시각이 기록됩니다.
     * 이후 조회 쿼리에서 자동으로 필터링됩니다.
     */
    @Transactional
    public void deleteProject(UUID userId, Long projectId) {
        Project project = findProjectWithOwnership(userId, projectId);
        project.softDelete();
    }

    // ══════════════════════════════════════
    // 3.10 버전 이름 수정
    // ══════════════════════════════════════

    /** 특정 버전의 이름을 변경합니다. */
    @Transactional
    public VersionRenameResponse renameVersion(UUID userId, Long projectId, Long versionId,
            VersionRenameRequest request) {
        findProjectWithOwnership(userId, projectId);
        ProjectVersion version = findVersion(projectId, versionId);
        version.rename(request.getName());

        return VersionRenameResponse.builder()
                .projectId(projectId)
                .versionId(version.getId())
                .name(version.getName())
                .updatedAt(version.getUpdatedAt())
                .build();
    }

    // ══════════════════════════════════════
    // 3.11 버전 삭제
    // ══════════════════════════════════════

    /**
     * 버전 삭제 — Soft Delete.
     *
     * <p>
     * <b>비즈니스 규칙:</b> 프로젝트에 최소 1개의 버전은 남아 있어야 합니다.
     * 마지막 버전 삭제를 시도하면 {@code CANNOT_DELETE_LAST_VERSION} 에러가 발생합니다.
     * 왜? → 버전이 없는 프로젝트는 비즈니스적으로 의미가 없으며, UI에서도 표시할 수 없습니다.
     * </p>
     */
    @Transactional
    public void deleteVersion(UUID userId, Long projectId, Long versionId) {
        findProjectWithOwnership(userId, projectId);
        ProjectVersion version = findVersion(projectId, versionId);

        // 마지막 버전 보호 — 최소 1개는 유지해야 함
        long versionCount = versionRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        if (versionCount <= 1) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_LAST_VERSION);
        }

        version.softDelete();
    }

    // ══════════════════════════════════════
    // 공개 + 내부 헬퍼 메서드
    // ══════════════════════════════════════

    /**
     * 소유권 검증 — SSE 진행률 조회 등 외부에서 호출할 수 있도록 public.
     * 내부적으로 {@code findProjectWithOwnership()}을 호출합니다.
     */
    public void verifyOwnership(UUID userId, Long projectId) {
        findProjectWithOwnership(userId, projectId);
    }

    /** 활성 프로필 조회 — 탈퇴한 사용자는 USER_NOT_FOUND. */
    private Profile findActiveProfile(UUID userId) {
        return profileRepository.findByIdAndIsActive(userId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 프로젝트 조회 + 소유권 검증을 한번에 수행합니다.
     *
     * <p>
     * 검증 순서가 중요합니다:
     * </p>
     * <ol>
     * <li>프로젝트 존재 확인 → {@code PROJECT_NOT_FOUND}</li>
     * <li>Soft Delete 확인 → {@code PROJECT_NOT_FOUND} (삭제된 것은 없는 것과 동일)</li>
     * <li>소유자 확인 → {@code ACCESS_DENIED} (다른 사용자의 프로젝트)</li>
     * </ol>
     */
    private Project findProjectWithOwnership(UUID userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.isDeleted()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (!project.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return project;
    }

    /**
     * 버전 조회 — 프로젝트 ID와 버전 ID를 함께 검증합니다.
     * 프로젝트에 속하지 않는 버전 ID를 요청하면 VERSION_NOT_FOUND.
     */
    private ProjectVersion findVersion(Long projectId, Long versionId) {
        ProjectVersion version = versionRepository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND));

        if (version.isDeleted()) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    /**
     * 트랙 정보를 일괄 저장합니다.
     * MIDI 파일 파싱 결과로 나온 각 트랙(0번=피아노, 1번=기타 등)을 DB에 저장합니다.
     */
    private void saveTracks(Project project, List<TrackItem> trackItems) {
        if (trackItems == null || trackItems.isEmpty()) {
            return;
        }
        for (TrackItem item : trackItems) {
            ProjectTrack track = ProjectTrack.builder()
                    .project(project)
                    .trackIndex(item.getTrackIndex())
                    .sourceInstrumentId(item.getSourceInstrumentId())
                    .build();
            trackRepository.save(track);
        }
    }

    /**
     * 버전별 악기 매핑 정보를 일괄 저장합니다.
     * "0번 트랙을 바이올린으로, 1번 트랙을 첼로로" 같은 편곡 설정을 저장합니다.
     */
    private void saveMappings(ProjectVersion version, List<MappingItem> mappingItems) {
        if (mappingItems == null || mappingItems.isEmpty()) {
            return;
        }
        // 매핑 대상이 유효한 카테고리인지 검증 (generatable 여부와 무관)
        for (MappingItem item : mappingItems) {
            if (!categoryRepository.existsById(item.getTargetInstrumentId())) {
                throw new BusinessException(ErrorCode.INVALID_INSTRUMENT_CATEGORY,
                        "존재하지 않는 악기 카테고리: " + item.getTargetInstrumentId());
            }
        }
        for (MappingItem item : mappingItems) {
            VersionMapping mapping = VersionMapping.builder()
                    .version(version)
                    .trackIndex(item.getTrackIndex())
                    .targetInstrumentId(item.getTargetInstrumentId())
                    .build();
            mappingRepository.save(mapping);
        }
    }

    /**
     * MIDI 파일 유효성 검증.
     * <ul>
     * <li>파일이 비어있으면 거부</li>
     * <li>.mid 또는 .midi 확장자만 허용</li>
     * <li>10MB 초과 시 거부</li>
     * </ul>
     */
    private void validateMidiFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".mid") && !filename.endsWith(".midi"))) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    /**
     * MIDI 파일을 Supabase Storage에 업로드합니다.
     * 경로: {userId}/{UUID}_{원본파일명}
     * UUID 접두어로 파일명 충돌을 방지합니다.
     *
     * @return Supabase Storage 내 경로 (bucket 제외)
     */
    private String saveMidiFile(UUID userId, MultipartFile file) {
        try {
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            String storagePath = userId.toString() + "/" + filename;

            storageService.upload(
                    SupabaseStorageService.BUCKET_MIDI,
                    storagePath,
                    file.getBytes(),
                    "audio/midi");

            return storagePath;
        } catch (IOException e) {
            log.error("MIDI 파일 저장 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 장르 문자열을 Genre enum으로 변환합니다.
     *
     * <ul>
     * <li>null 또는 빈 문자열 → {@link Genre#CLASSICAL} (기본값)</li>
     * <li>유효하지 않은 값 → {@link ErrorCode#INVALID_GENRE} 예외</li>
     * </ul>
     */
    private Genre resolveGenre(String genreStr) {
        if (genreStr == null || genreStr.isBlank()) {
            return Genre.CLASSICAL;
        }
        try {
            return Genre.valueOf(genreStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_GENRE);
        }
    }
}
