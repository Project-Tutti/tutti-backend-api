package com.tutti.server.domain.instrument.controller;

import com.tutti.server.domain.instrument.dto.InstrumentCategoryResponse;
import com.tutti.server.domain.instrument.dto.InstrumentListResponse;
import com.tutti.server.domain.instrument.dto.InstrumentResponse;
import com.tutti.server.domain.instrument.service.InstrumentService;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 악기 카테고리 & 악기 목록 API — 인증 없이 접근 가능합니다.
 *
 * <ul>
 * <li>{@code GET /api/instruments/categories} — 전체 카테고리 목록 (요약)</li>
 * <li>{@code GET /api/instruments/categories/generatable} — AI 생성 가능 카테고리 + 세부 악기 목록</li>
 * <li>{@code GET /api/instruments} — 전체 활성 악기 목록 (플랫)</li>
 * <li>{@code GET /api/instruments/categories/{categoryId}} — 특정 카테고리 소속 악기 목록</li>
 * </ul>
 */
@Tag(name = "Instrument", description = "악기 카테고리 & 마스터 데이터 API")
@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentService instrumentService;

    @Operation(summary = "전체 카테고리 목록", description = "13개 악기 카테고리 요약을 조회합니다.")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<InstrumentCategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success("카테고리 목록을 조회하였습니다.",
                instrumentService.getAllCategories()));
    }

    @Operation(summary = "생성 가능 카테고리 + 악기",
            description = "AI 편곡이 가능한 카테고리와 세부 악기 목록을 조회합니다.")
    @GetMapping("/categories/generatable")
    public ResponseEntity<ApiResponse<List<InstrumentCategoryResponse>>> getGeneratableCategories() {
        return ResponseEntity.ok(ApiResponse.success("생성 가능 카테고리를 조회하였습니다.",
                instrumentService.getGeneratableCategories()));
    }

    @Operation(summary = "전체 악기 목록", description = "활성 상태인 모든 악기를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<InstrumentListResponse>> getAllInstruments() {
        List<InstrumentResponse> instruments = instrumentService.getAllActiveInstruments();
        return ResponseEntity.ok(ApiResponse.success("악기 목록을 조회하였습니다.",
                new InstrumentListResponse(instruments)));
    }

    @Operation(summary = "카테고리별 악기 목록",
            description = "특정 카테고리에 속한 악기만 조회합니다.")
    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<List<InstrumentResponse>>> getInstrumentsByCategory(
            @PathVariable Integer categoryId) {
        return ResponseEntity.ok(ApiResponse.success("카테고리 악기 목록을 조회하였습니다.",
                instrumentService.getInstrumentsByCategory(categoryId)));
    }
}
