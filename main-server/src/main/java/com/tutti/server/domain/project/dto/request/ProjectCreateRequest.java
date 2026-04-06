package com.tutti.server.domain.project.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** 생성 대상 카테고리의 representative_program (예: 40=Solo String). */
    private Integer instrumentId;

    /** 생성 음역대 하한 (MIDI 0~127). null이면 악기 기본값 사용. */
    @Min(value = 0, message = "minNote는 0 이상이어야 합니다.")
    @Max(value = 127, message = "minNote는 127 이하여야 합니다.")
    private Integer minNote;

    /** 생성 음역대 상한 (MIDI 0~127). null이면 악기 기본값 사용. */
    @Min(value = 0, message = "maxNote는 0 이상이어야 합니다.")
    @Max(value = 127, message = "maxNote는 127 이하여야 합니다.")
    private Integer maxNote;

    /** 생성 장르. null이면 CLASSICAL 기본값 사용. */
    private String genre;

    /** 생성 temperature (0.1~2.0). null이면 1.0 기본값 사용. */
    @DecimalMin(value = "0.1", message = "temperature는 0.1 이상이어야 합니다.")
    @DecimalMax(value = "2.0", message = "temperature는 2.0 이하여야 합니다.")
    private Double temperature;

    @Valid
    private List<TrackItem> tracks;

    @Valid
    private List<MappingItem> mappings;
}

