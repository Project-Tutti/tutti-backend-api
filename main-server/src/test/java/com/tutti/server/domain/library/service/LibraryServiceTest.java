package com.tutti.server.domain.library.service;

import com.tutti.server.domain.library.dto.response.LibraryListResponse;
import com.tutti.server.domain.project.entity.Project;
import com.tutti.server.domain.project.repository.ProjectRepository;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import com.tutti.server.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LibraryService 단위 테스트")
class LibraryServiceTest {

    @InjectMocks
    private LibraryService libraryService;

    @Mock
    private ProjectRepository projectRepository;

    @Nested
    @DisplayName("getLibrary()")
    class GetLibrary {

        @Test
        @DisplayName("정상 조회 — 키워드 없이 전체 목록")
        void 정상_전체조회() {
            // given
            Project project = TestFixtures.createProject(TestFixtures.createActiveProfile());
            Page<Project> page = new PageImpl<>(List.of(project));
            given(projectRepository.findByUserIdAndDeletedAtIsNull(eq(TestFixtures.USER_ID), any(Pageable.class)))
                    .willReturn(page);

            // when
            LibraryListResponse result = libraryService.getLibrary(
                    TestFixtures.USER_ID, 0, 10, null, null);

            // then
            assertThat(result.getProjects()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("키워드 검색 — 필터링 동작")
        void 키워드_검색() {
            // given
            Page<Project> emptyPage = new PageImpl<>(Collections.emptyList());
            given(projectRepository.findByUserIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    eq(TestFixtures.USER_ID), eq("없는키워드"), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            LibraryListResponse result = libraryService.getLibrary(
                    TestFixtures.USER_ID, 0, 10, "없는키워드", null);

            // then
            assertThat(result.getProjects()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("빈 결과 — hasNext=false")
        void 빈결과() {
            // given
            Page<Project> emptyPage = new PageImpl<>(Collections.emptyList());
            given(projectRepository.findByUserIdAndDeletedAtIsNull(eq(TestFixtures.USER_ID), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            LibraryListResponse result = libraryService.getLibrary(
                    TestFixtures.USER_ID, 0, 10, null, null);

            // then
            assertThat(result.isHasNext()).isFalse();
        }

        @Test
        @DisplayName("잘못된 정렬 파라미터 — INVALID_SORT_PARAMETER 예외")
        void 잘못된_정렬() {
            // when & then
            assertThatThrownBy(() -> libraryService.getLibrary(TestFixtures.USER_ID, 0, 10, null, "invalidField,asc"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SORT_PARAMETER);
        }

        @Test
        @DisplayName("잘못된 정렬 방향 — INVALID_SORT_PARAMETER 예외")
        void 잘못된_정렬방향() {
            // when & then
            assertThatThrownBy(() -> libraryService.getLibrary(TestFixtures.USER_ID, 0, 10, null, "name,invalid"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SORT_PARAMETER);
        }

        @Test
        @DisplayName("음수 페이지 — 0으로 보정되어 정상 동작")
        void 음수페이지_보정() {
            // given
            Page<Project> emptyPage = new PageImpl<>(Collections.emptyList());
            given(projectRepository.findByUserIdAndDeletedAtIsNull(eq(TestFixtures.USER_ID), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when — 음수 page가 0으로 보정됨
            LibraryListResponse result = libraryService.getLibrary(
                    TestFixtures.USER_ID, -5, 10, null, null);

            // then
            assertThat(result.getCurrentPage()).isZero();
        }
    }
}
