package com.tutti.server.domain.project.service;

import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import com.tutti.server.domain.project.dto.request.MappingItem;
import com.tutti.server.domain.project.dto.request.ProjectRenameRequest;
import com.tutti.server.domain.project.dto.request.RegenerateRequest;
import com.tutti.server.domain.project.dto.request.VersionRenameRequest;
import com.tutti.server.domain.project.dto.response.ProjectDetailResponse;
import com.tutti.server.domain.project.dto.response.TrackInfoResponse;
import com.tutti.server.domain.project.entity.Project;
import com.tutti.server.domain.project.entity.ProjectVersion;
import com.tutti.server.domain.project.repository.*;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import com.tutti.server.infra.storage.SupabaseStorageService;
import com.tutti.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService 단위 테스트")
class ProjectServiceTest {

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectVersionRepository versionRepository;
    @Mock
    private ProjectTrackRepository trackRepository;
    @Mock
    private VersionMappingRepository mappingRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private InstrumentCategoryRepository categoryRepository;
    @Mock
    private ArrangementService arrangementService;
    @Mock
    private SupabaseStorageService storageService;

    private Profile owner;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = TestFixtures.createActiveProfile();
        project = TestFixtures.createProject(owner);
    }

    // ══════════════════════════════════════
    // 3.2 프로젝트 조회
    // ══════════════════════════════════════

    @Nested
    @DisplayName("getProject()")
    class GetProject {

        @Test
        @DisplayName("정상 조회 — 버전 목록 포함 응답")
        void 정상_프로젝트조회() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByProjectIdWithMappings(1L)).willReturn(Collections.emptyList());

            // when
            ProjectDetailResponse result = projectService.getProject(TestFixtures.USER_ID, 1L);

            // then
            assertThat(result.getProjectId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 프로젝트");
        }

        @Test
        @DisplayName("존재하지 않는 프로젝트 — PROJECT_NOT_FOUND 예외")
        void 존재하지않는_프로젝트() {
            // given
            given(projectRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> projectService.getProject(TestFixtures.USER_ID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("Soft Delete된 프로젝트 — PROJECT_NOT_FOUND 예외")
        void 삭제된_프로젝트() {
            // given
            Project deleted = TestFixtures.createDeletedProject(owner);
            given(projectRepository.findById(1L)).willReturn(Optional.of(deleted));

            // when & then
            assertThatThrownBy(() -> projectService.getProject(TestFixtures.USER_ID, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 프로젝트 — ACCESS_DENIED 예외")
        void 다른사용자_프로젝트() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));

            // when & then
            assertThatThrownBy(() -> projectService.getProject(TestFixtures.OTHER_USER_ID, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ══════════════════════════════════════
    // 3.3 트랙 정보 조회
    // ══════════════════════════════════════

    @Nested
    @DisplayName("getTracks()")
    class GetTracks {

        @Test
        @DisplayName("정상 트랙 조회 — 빈 목록도 정상")
        void 정상_트랙조회() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(trackRepository.findByProjectId(1L)).willReturn(Collections.emptyList());

            // when
            TrackInfoResponse result = projectService.getTracks(TestFixtures.USER_ID, 1L);

            // then
            assertThat(result.getTracks()).isEmpty();
        }
    }

    // ══════════════════════════════════════
    // 3.5 재생성
    // ══════════════════════════════════════

    @Nested
    @DisplayName("regenerate()")
    class Regenerate {

        @Test
        @DisplayName("정상 재생성 — 새 버전 생성 + AI 요청")
        void 정상_재생성() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.countByProjectIdAndDeletedAtIsNull(1L)).willReturn(1L);
            given(versionRepository.save(org.mockito.ArgumentMatchers.any(ProjectVersion.class)))
                    .willAnswer(inv -> {
                        ProjectVersion v = inv.getArgument(0);
                        ReflectionTestUtils.setField(v, "id", 2L);
                        return v;
                    });

            // Fallback: 직전 버전 없음 (처음 재생성)
            given(versionRepository.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.empty());

            // targetInstrumentId=40 (Solo String 카테고리)
            RegenerateRequest request = createRegenerateRequest(null,
                    List.of(new MappingItem(0, 40)),
                    40, null, null);

            // mock: targetInstrumentId=40이 유효한 카테고리
            given(categoryRepository.existsById(40))
                    .willReturn(true);

            // when
            var result = projectService.regenerate(TestFixtures.USER_ID, 1L, request);

            // then
            assertThat(result.getProjectId()).isEqualTo(1L);
            assertThat(result.getVersionId()).isEqualTo(2L);
            assertThat(result.getStatus()).isEqualTo("processing");
        }
    }

    // ══════════════════════════════════════
    // 3.7 파일 다운로드
    // ══════════════════════════════════════

    @Nested
    @DisplayName("downloadFile()")
    class DownloadFile {

        @Test
        @DisplayName("지원하지 않는 파일 타입 — INVALID_FILE_TYPE 예외")
        void 잘못된_파일타입() {
            // when & then
            assertThatThrownBy(
                    () -> projectService.downloadFile(TestFixtures.USER_ID, 1L, 1L, "mp3"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("처리 미완료 버전 — PROCESSING_NOT_COMPLETE 예외")
        void 처리_미완료() {
            // given
            ProjectVersion pending = TestFixtures.createPendingVersion(project);
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByIdAndProjectId(1L, 1L)).willReturn(Optional.of(pending));

            // when & then
            assertThatThrownBy(
                    () -> projectService.downloadFile(TestFixtures.USER_ID, 1L, 1L, "midi"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROCESSING_NOT_COMPLETE);
        }
    }

    // ══════════════════════════════════════
    // 3.8 프로젝트 이름 수정
    // ══════════════════════════════════════

    @Nested
    @DisplayName("renameProject()")
    class RenameProject {

        @Test
        @DisplayName("정상 이름 수정")
        void 정상_이름수정() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            ProjectRenameRequest request = new ProjectRenameRequest();
            ReflectionTestUtils.setField(request, "name", "새 이름");

            // when
            var result = projectService.renameProject(TestFixtures.USER_ID, 1L, request);

            // then
            assertThat(result.getName()).isEqualTo("새 이름");
        }
    }

    // ══════════════════════════════════════
    // 3.9 프로젝트 삭제
    // ══════════════════════════════════════

    @Nested
    @DisplayName("deleteProject()")
    class DeleteProject {

        @Test
        @DisplayName("정상 삭제 — Soft Delete 적용")
        void 정상_삭제() {
            // given
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));

            // when
            projectService.deleteProject(TestFixtures.USER_ID, 1L);

            // then
            assertThat(project.isDeleted()).isTrue();
        }
    }

    // ══════════════════════════════════════
    // 3.10 버전 이름 수정
    // ══════════════════════════════════════

    @Nested
    @DisplayName("renameVersion()")
    class RenameVersion {

        @Test
        @DisplayName("정상 버전 이름 수정")
        void 정상_버전이름수정() {
            // given
            ProjectVersion version = TestFixtures.createPendingVersion(project);
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByIdAndProjectId(1L, 1L)).willReturn(Optional.of(version));

            VersionRenameRequest request = new VersionRenameRequest();
            ReflectionTestUtils.setField(request, "name", "New V1");

            // when
            var result = projectService.renameVersion(TestFixtures.USER_ID, 1L, 1L, request);

            // then
            assertThat(result.getName()).isEqualTo("New V1");
        }

        @Test
        @DisplayName("Soft Delete된 버전 — VERSION_NOT_FOUND 예외")
        void 삭제된버전_이름수정() {
            // given
            ProjectVersion deleted = TestFixtures.createDeletedVersion(project);
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByIdAndProjectId(1L, 1L)).willReturn(Optional.of(deleted));

            VersionRenameRequest request = new VersionRenameRequest();
            ReflectionTestUtils.setField(request, "name", "X");

            // when & then
            assertThatThrownBy(
                    () -> projectService.renameVersion(TestFixtures.USER_ID, 1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VERSION_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════
    // 3.11 버전 삭제
    // ══════════════════════════════════════

    @Nested
    @DisplayName("deleteVersion()")
    class DeleteVersion {

        @Test
        @DisplayName("정상 버전 삭제 — 2개 이상일 때만 가능")
        void 정상_버전삭제() {
            // given
            ProjectVersion version = TestFixtures.createPendingVersion(project);
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByIdAndProjectId(1L, 1L)).willReturn(Optional.of(version));
            given(versionRepository.countByProjectIdAndDeletedAtIsNull(1L)).willReturn(2L);

            // when
            projectService.deleteVersion(TestFixtures.USER_ID, 1L, 1L);

            // then
            assertThat(version.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("마지막 버전 삭제 시도 — CANNOT_DELETE_LAST_VERSION 예외")
        void 마지막버전_삭제불가() {
            // given
            ProjectVersion version = TestFixtures.createPendingVersion(project);
            given(projectRepository.findById(1L)).willReturn(Optional.of(project));
            given(versionRepository.findByIdAndProjectId(1L, 1L)).willReturn(Optional.of(version));
            given(versionRepository.countByProjectIdAndDeletedAtIsNull(1L)).willReturn(1L);

            // when & then
            assertThatThrownBy(() -> projectService.deleteVersion(TestFixtures.USER_ID, 1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_DELETE_LAST_VERSION);
        }
    }

    // ── DTO Helper ──

    private RegenerateRequest createRegenerateRequest(String versionName,
                                                      List<MappingItem> mappings,
                                                      Integer instrumentId,
                                                      Integer minNote,
                                                      Integer maxNote) {
        RegenerateRequest req = new RegenerateRequest();
        ReflectionTestUtils.setField(req, "versionName", versionName);
        ReflectionTestUtils.setField(req, "mappings", mappings);
        ReflectionTestUtils.setField(req, "instrumentId", instrumentId);
        ReflectionTestUtils.setField(req, "minNote", minNote);
        ReflectionTestUtils.setField(req, "maxNote", maxNote);
        return req;
    }
}
