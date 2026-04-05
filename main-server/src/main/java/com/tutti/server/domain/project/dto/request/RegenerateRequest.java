package com.tutti.server.domain.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RegenerateRequest {

    private String versionName;

    /** 변경할 카테고리의 representative_program. null이면 직전 버전 값 유지. */
    private Integer instrumentId;

    /** 변경할 생성 음역대 하한. null이면 직전 버전 값 유지. */
    @Min(value = 0, message = "minNote는 0 이상이어야 합니다.")
    @Max(value = 127, message = "minNote는 127 이하여야 합니다.")
    private Integer minNote;

    /** 변경할 생성 음역대 상한. null이면 직전 버전 값 유지. */
    @Min(value = 0, message = "maxNote는 0 이상이어야 합니다.")
    @Max(value = 127, message = "maxNote는 127 이하여야 합니다.")
    private Integer maxNote;

    @NotEmpty(message = "매핑 정보는 필수입니다.")
    @Valid
    private List<MappingItem> mappings;
}
