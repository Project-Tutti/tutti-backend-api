package com.tutti.server.domain.project.repository;

import com.tutti.server.domain.project.entity.VersionMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionMappingRepository extends JpaRepository<VersionMapping, Long> {

    List<VersionMapping> findByVersionId(Long versionId);
}
