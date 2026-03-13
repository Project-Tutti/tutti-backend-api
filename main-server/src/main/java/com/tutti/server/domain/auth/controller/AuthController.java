package com.tutti.server.domain.auth.controller;

import com.tutti.server.domain.auth.dto.request.LoginRequest;
import com.tutti.server.domain.auth.dto.request.SignupRequest;
import com.tutti.server.domain.auth.dto.request.SocialLoginRequest;
import com.tutti.server.domain.auth.dto.request.TokenRefreshRequest;
import com.tutti.server.domain.auth.dto.response.AuthResponse;
import com.tutti.server.domain.auth.dto.response.TokenResponse;
import com.tutti.server.domain.auth.service.AuthService;
import com.tutti.server.global.auth.AuthUtils;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 인증 컨트롤러 — 회원가입, 로그인, 토큰 갱신, 로그아웃 API를 제공합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client HTTP 요청] → <b>AuthController</b> (파라미터 검증)
 *                    → AuthService (비즈니스 로직)
 *                    → Repository (DB)
 *                    → [Client HTTP 응답]
 * </pre>
 *
 * <h3>Controller의 역할</h3>
 * <ul>
 * <li>HTTP 요청/응답 매핑 (URL → 메서드)</li>
 * <li>요청 DTO의 유효성 검증 ({@code @Valid})</li>
 * <li>인증된 사용자 ID 추출 ({@code AuthUtils.extractUserId()})</li>
 * <li>비즈니스 로직은 Service에 위임 (Controller에 로직을 두지 않음)</li>
 * </ul>
 *
 * <h3>인증 정책</h3>
 * <ul>
 * <li>{@code /api/auth/signup}, {@code /api/auth/login},
 * {@code /api/auth/social},
 * {@code /api/auth/refresh} — 인증 불필요 (SecurityConfig에서 permitAll)</li>
 * <li>{@code /api/auth/logout} — JWT 토큰 필요 (인증된 사용자만)</li>
 * </ul>
 *
 * @Tag: Swagger UI에서 API를 "Auth" 그룹으로 묶어 표시합니다.
 * @RestController: @Controller + @ResponseBody를 합친 것으로,
 *                  반환값을 JSON으로 자동 변환합니다 (Jackson 라이브러리 사용).
 *                  @RequestMapping("/api/auth"): 이 Controller의 모든 엔드포인트에
 *                  "/api/auth" 접두어가 붙습니다.
 *
 * @see AuthService
 */
@Tag(name = "Auth", description = "인증 및 계정 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── 2.1 회원가입 ──

    /**
     * 신규 사용자 계정 생성.
     *
     * @Valid: SignupRequest 내부의 @NotBlank, @Email 등 검증을 실행합니다.
     *         검증 실패 시 MethodArgumentNotValidException → GlobalExceptionHandler →
     *         400 에러.
     * @RequestBody: HTTP 요청 본문(JSON)을 자동으로 SignupRequest 객체로 변환합니다.
     *
     * @return 201 Created + JWT 토큰 + 사용자 프로필
     */
    @Operation(summary = "회원가입", description = "신규 사용자 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse result = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", result));
    }

    // ── 2.2 이메일 중복 확인 ──

    /**
     * 이메일 사용 가능 여부 확인 — 가입 폼에서 실시간 중복 검사용.
     *
     * @RequestParam: URL 쿼리 파라미터를 매핑합니다.
     *                예: GET /api/auth/check-email?email=test@tutti.com
     * @Email: 이메일 형식이 아니면 ConstraintViolationException → 400 에러.
     */
    @Operation(summary = "이메일 중복 확인", description = "이메일 사용 가능 여부를 확인합니다.")
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(
            @RequestParam @Email(message = "올바른 이메일 형식이 아닙니다.") String email) {
        authService.checkEmail(email);
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일입니다."));
    }

    // ── 2.3 일반 로그인 ──

    /** 이메일 + 비밀번호 로그인 → JWT Access Token + Refresh Token 발급. */
    @Operation(summary = "일반 로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse result = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", result));
    }

    // ── 2.4 소셜 로그인 ──

    /** OAuth 2.0 인가 코드로 소셜 로그인 — 기존 유저 로그인 or 신규 자동 가입. */
    @Operation(summary = "소셜 로그인", description = "OAuth 2.0 기반 소셜 로그인을 처리합니다.")
    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponse>> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        AuthResponse result = authService.socialLogin(request);
        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", result));
    }

    // ── 2.5 토큰 갱신 ──

    /** 만료된 Access Token 갱신 — Refresh Token Rotation 적용. */
    @Operation(summary = "토큰 갱신", description = "만료된 Access Token을 갱신합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse result = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("토큰이 갱신되었습니다.", result));
    }

    // ── 2.6 로그아웃 ──

    /**
     * 로그아웃 — 해당 사용자의 모든 Refresh Token을 삭제합니다.
     *
     * <p>
     * {@code Authentication authentication}: Spring Security가 JWT 토큰에서
     * 사용자 정보를 추출하여 자동으로 주입하는 파라미터입니다.
     * {@code AuthUtils.extractUserId()}로 UUID를 안전하게 파싱합니다.
     * </p>
     */
    @Operation(summary = "로그아웃", description = "현재 세션을 종료하고 Refresh Token을 무효화합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        UUID userId = AuthUtils.extractUserId(authentication);
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success("성공적으로 로그아웃 되었습니다."));
    }
}
