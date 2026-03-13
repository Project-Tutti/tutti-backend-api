package com.tutti.server.domain.project.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class VersionRenameResponse {

    private Long projectId;
    private Long versionId;
    private String name;
    private LocalDateTime updatedAt;
}
