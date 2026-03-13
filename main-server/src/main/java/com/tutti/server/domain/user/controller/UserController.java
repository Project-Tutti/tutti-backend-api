package com.tutti.server.domain.user.controller;

import com.tutti.server.domain.user.dto.response.UserProfileResponse;
import com.tutti.server.domain.user.service.UserService;
import com.tutti.server.global.auth.AuthUtils;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 사용자 컨트롤러 — 프로필 조회와 회원 탈퇴 API를 제공합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client HTTP] → <b>UserController</b> (인증 + UUID 추출)
 *               → UserService (비즈니스 로직)
 *               → ProfileRepository, RefreshTokenRepository (DB)
 * </pre>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 * <li>{@code GET /api/users/me} — 내 프로필 조회</li>
 * <li>{@code DELETE /api/users/me} — 회원 탈퇴 (Soft Delete)</li>
 * </ul>
 *
 * <p>
 * 모든 엔드포인트는 JWT 인증이 필요합니다.
 * {@code /me} 패턴을 사용하여 "현재 로그인한 사용자"를 의미합니다.
 * </p>
 *
 * @see UserService
 */
@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── 2.7 내 프로필 조회 ──

    /**
     * 현재 로그인한 사용자의 프로필 정보 조회.
     *
     * <p>
     * {@code Authentication authentication}: Spring Security의
     * JwtAuthenticationFilter가
     * HTTP 요청의 'Authorization: Bearer {token}' 헤더를 파싱하여
     * 자동으로 이 파라미터에 주입합니다. Service에서 직접 JWT를 파싱할 필요가 없습니다.
     * </p>
     */
    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication authentication) {
        UUID userId = AuthUtils.extractUserId(authentication);
        UserProfileResponse result = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("프로필 조회에 성공하였습니다.", result));
    }

    // ── 2.8 회원 탈퇴 ──

    /**
     * 회원 탈퇴 (Soft Delete).
     *
     * <p>
     * 탈퇴 시 수행되는 작업:
     * </p>
     * <ol>
     * <li>Profile의 isActive = false, deletedAt = now()</li>
     * <li>해당 사용자의 모든 Refresh Token 삭제 (즉시 로그아웃 효과)</li>
     * </ol>
     */
    @Operation(summary = "회원 탈퇴", description = "사용자 계정 및 연관 데이터를 삭제(Soft Delete)합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(Authentication authentication) {
        UUID userId = AuthUtils.extractUserId(authentication);
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
    }
}
