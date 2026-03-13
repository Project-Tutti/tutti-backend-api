package com.tutti.server.domain.project.repository;

import com.tutti.server.domain.project.entity.ProjectTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTrackRepository extends JpaRepository<ProjectTrack, Long> {

    List<ProjectTrack> findByProjectId(Long projectId);
}
