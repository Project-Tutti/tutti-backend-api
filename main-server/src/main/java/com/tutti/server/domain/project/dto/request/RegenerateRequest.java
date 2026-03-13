package com.tutti.server.domain.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RegenerateRequest {

    private String versionName;

    @NotEmpty(message = "매핑 정보는 필수입니다.")
    @Valid
    private List<MappingItem> mappings;
}
