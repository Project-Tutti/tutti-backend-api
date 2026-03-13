package com.tutti.server.domain.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ProjectCreateRequest {

    @NotBlank(message = "프로젝트 이름은 필수입니다.")
    @Size(max = 100, message = "프로젝트 이름은 최대 100자입니다.")
    private String name;

    private String versionName;

    private Integer instrumentId;

    @Valid
    private List<TrackItem> tracks;

    @Valid
    private List<MappingItem> mappings;
}
