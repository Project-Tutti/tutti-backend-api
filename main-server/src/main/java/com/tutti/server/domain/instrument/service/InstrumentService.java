package com.tutti.server.domain.instrument.service;

import com.tutti.server.domain.instrument.dto.InstrumentCategoryResponse;
import com.tutti.server.domain.instrument.dto.InstrumentResponse;
import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import com.tutti.server.domain.instrument.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentCategoryRepository categoryRepository;
    private final InstrumentRepository instrumentRepository;

    /** 전체 카테고리 목록 조회 (요약). */
    public List<InstrumentCategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderByRepresentativeProgramAsc().stream()
                .map(InstrumentCategoryResponse::summaryFrom)
                .toList();
    }

    /** 생성 가능한 카테고리 + 세부 악기 목록 조회. */
    public List<InstrumentCategoryResponse> getGeneratableCategories() {
        return categoryRepository.findGeneratableWithInstruments().stream()
                .map(InstrumentCategoryResponse::detailFrom)
                .toList();
    }

    /** 전체 활성 악기 목록 조회 (플랫 리스트). */
    public List<InstrumentResponse> getAllActiveInstruments() {
        return instrumentRepository.findByActiveTrueOrderByMidiProgram().stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    /** 특정 카테고리에 속한 악기 목록 조회. */
    public List<InstrumentResponse> getInstrumentsByCategory(Integer categoryId) {
        return instrumentRepository.findByCategoryId(categoryId).stream()
                .map(InstrumentResponse::from)
                .toList();
    }
}
