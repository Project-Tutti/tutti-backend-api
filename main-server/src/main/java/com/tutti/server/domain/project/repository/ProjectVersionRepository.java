package com.tutti.server.domain.project.repository;

import com.tutti.server.domain.project.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Long> {

    // Soft Delete 필터링 적용
    List<ProjectVersion> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectVersion> findByIdAndProjectId(Long id, Long projectId);

    long countByProjectIdAndDeletedAtIsNull(Long projectId);

    /** 재생성 Fallback용 — 가장 최근 버전 조회 (Soft Delete 제외). */
    Optional<ProjectVersion> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);

    // N+1 방지: 버전 + 매핑 한번에 fetch join
    @Query("SELECT DISTINCT v FROM ProjectVersion v " +
            "LEFT JOIN FETCH v.mappings " +
            "WHERE v.project.id = :projectId AND v.deletedAt IS NULL " +
            "ORDER BY v.createdAt DESC")
    List<ProjectVersion> findByProjectIdWithMappings(@Param("projectId") Long projectId);

    /** 가비지 컬렉터를 위한 쿼리: 지정된 시각 이전에 업데이트가 멈춘 특정 상태의 버전을 찾습니다. */
    List<ProjectVersion> findByStatusAndUpdatedAtBefore(ProjectVersion.VersionStatus status, java.time.LocalDateTime threshold);
}
