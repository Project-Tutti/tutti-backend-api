package com.tutti.server.domain.project.repository;

import com.tutti.server.domain.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndUserId(Long id, UUID userId);

    // Soft Delete 필터링: deleted_at IS NULL
    Page<Project> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Project> findByUserIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID userId, String keyword, Pageable pageable);

    /** 사용자의 모든 프로젝트 조회 — 계정 재활성화 시 스토리지 파일 경로 수집용. */
    List<Project> findAllByUserId(UUID userId);

    // ── 계정 재활성화용 Bulk Delete (FK 의존 순서대로 실행) ──

    /** 1단계: 사용자의 모든 version_mappings 삭제 */
    @Modifying
    @Query("DELETE FROM VersionMapping vm WHERE vm.version.id IN " +
            "(SELECT v.id FROM ProjectVersion v WHERE v.project.user.id = :userId)")
    void bulkDeleteVersionMappingsByUserId(@Param("userId") UUID userId);

    /** 2단계: 사용자의 모든 project_versions 삭제 */
    @Modifying
    @Query("DELETE FROM ProjectVersion v WHERE v.project.user.id = :userId")
    void bulkDeleteVersionsByUserId(@Param("userId") UUID userId);

    /** 3단계: 사용자의 모든 project_tracks 삭제 */
    @Modifying
    @Query("DELETE FROM ProjectTrack t WHERE t.project.user.id = :userId")
    void bulkDeleteTracksByUserId(@Param("userId") UUID userId);

    /** 4단계: 사용자의 모든 projects 삭제 */
    @Modifying
    @Query("DELETE FROM Project p WHERE p.user.id = :userId")
    void bulkDeleteProjectsByUserId(@Param("userId") UUID userId);
}
