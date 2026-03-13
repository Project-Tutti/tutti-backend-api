package com.tutti.server.domain.project.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MappingItem {

    @NotNull(message = "트랙 번호는 필수입니다.")
    private Integer trackIndex;

    @NotNull(message = "대상 악기 ID는 필수입니다.")
    private Integer targetInstrumentId;
}
