package com.tutti.server.domain.instrument.dto;

import com.tutti.server.domain.instrument.entity.InstrumentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 카테고리 단위로 악기 목록을 반환하는 응답 DTO.
 * 생성 가능한 카테고리 조회 시 세부 악기 목록을 포함합니다.
 */
@Getter
@Builder
@AllArgsConstructor
public class InstrumentCategoryResponse {

    private Integer representativeProgram;
    private String name;
    private boolean generatable;
    private List<InstrumentResponse> instruments;

    /** 악기 목록 없이 카테고리 요약만 반환. */
    public static InstrumentCategoryResponse summaryFrom(InstrumentCategory category) {
        return InstrumentCategoryResponse.builder()
                .representativeProgram(category.getRepresentativeProgram())
                .name(category.getName())
                .generatable(category.isGeneratable())
                .build();
    }

    /** 카테고리 + 세부 악기 목록 함께 반환. */
    public static InstrumentCategoryResponse detailFrom(InstrumentCategory category) {
        List<InstrumentResponse> instrumentDtos = category.getInstruments().stream()
                .filter(i -> i.isActive())
                .map(InstrumentResponse::from)
                .toList();

        return InstrumentCategoryResponse.builder()
                .representativeProgram(category.getRepresentativeProgram())
                .name(category.getName())
                .generatable(category.isGeneratable())
                .instruments(instrumentDtos)
                .build();
    }
}
