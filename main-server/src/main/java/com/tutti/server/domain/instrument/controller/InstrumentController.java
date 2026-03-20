package com.tutti.server.domain.instrument.controller;

import com.tutti.server.domain.instrument.dto.InstrumentListResponse;
import com.tutti.server.domain.instrument.dto.InstrumentResponse;
import com.tutti.server.domain.instrument.repository.InstrumentRepository;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 악기 목록 API — 인증 없이 접근 가능합니다.
 *
 * <ul>
 * <li>{@code GET /api/instruments} — 전체 악기 목록 (MIDI 파싱 시 원본 악기 표시용)</li>
 * <li>{@code GET /api/instruments/generatable} — AI 생성 가능 악기만 (편곡 대상 선택용)</li>
 * </ul>
 */
@Tag(name = "Instrument", description = "악기 마스터 데이터 API")
@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentRepository instrumentRepository;

    @Operation(summary = "전체 악기 목록", description = "활성 상태인 모든 악기를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<InstrumentListResponse>> getAllInstruments() {
        List<InstrumentResponse> instruments = instrumentRepository.findByActiveTrueOrderByMidiProgram()
                .stream()
                .map(InstrumentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("악기 목록을 조회하였습니다.",
                new InstrumentListResponse(instruments)));
    }

    @Operation(summary = "생성 가능 악기 목록", description = "AI 편곡이 가능한 악기만 조회합니다.")
    @GetMapping("/generatable")
    public ResponseEntity<ApiResponse<InstrumentListResponse>> getGeneratableInstruments() {
        List<InstrumentResponse> instruments = instrumentRepository
                .findByActiveTrueAndGeneratableTrueOrderByMidiProgram()
                .stream()
                .map(InstrumentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("생성 가능 악기 목록을 조회하였습니다.",
                new InstrumentListResponse(instruments)));
    }
}
