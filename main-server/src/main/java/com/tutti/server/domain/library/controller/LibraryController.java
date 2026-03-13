package com.tutti.server.domain.library.controller;

import com.tutti.server.domain.library.dto.response.LibraryListResponse;
import com.tutti.server.domain.library.service.LibraryService;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Library", description = "보관함 관리 API")
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    // ── 4.1 보관함 목록 조회 ──

    @Operation(summary = "보관함 목록 조회", description = "사용자의 프로젝트 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<LibraryListResponse>> getLibrary(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        UUID userId = UUID.fromString(authentication.getName());
        LibraryListResponse result = libraryService.getLibrary(userId, page, size, keyword, sort);
        return ResponseEntity.ok(ApiResponse.success("보관함 목록을 조회하였습니다.", result));
    }
}
