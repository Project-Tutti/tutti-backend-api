package com.tutti.server.domain.project.dto.response;

import com.tutti.server.domain.project.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ProjectDetailResponse {

    private Long projectId;
    private String name;
    private String originalFileName;
    private LocalDateTime createdAt;
    private List<VersionResponse> versions;

    public static ProjectDetailResponse from(Project project, List<VersionResponse> versions) {
        return ProjectDetailResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .originalFileName(project.getOriginalFileName())
                .createdAt(project.getCreatedAt())
                .versions(versions)
                .build();
    }
}
