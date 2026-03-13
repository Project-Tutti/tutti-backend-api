package com.tutti.server.domain.project.repository;

import com.tutti.server.domain.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndUserId(Long id, UUID userId);

    // Soft Delete 필터링: deleted_at IS NULL
    Page<Project> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Project> findByUserIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID userId, String keyword, Pageable pageable);
}
