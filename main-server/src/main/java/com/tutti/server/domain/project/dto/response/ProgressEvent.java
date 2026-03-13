package com.tutti.server.domain.project.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProgressEvent {

    private Long projectId;
    private Long versionId;
    private String status;
    private Integer progress;
}
