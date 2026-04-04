package com.tutti.server.domain.auth.service;

import com.tutti.server.domain.auth.dto.request.LoginRequest;
import com.tutti.server.domain.auth.dto.request.SignupRequest;
import com.tutti.server.domain.auth.dto.request.SocialLoginRequest;
import com.tutti.server.domain.auth.dto.request.TokenRefreshRequest;
import com.tutti.server.domain.auth.dto.response.AuthResponse;
import com.tutti.server.domain.auth.dto.response.TokenResponse;
import com.tutti.server.domain.auth.entity.RefreshToken;
import com.tutti.server.domain.auth.repository.RefreshTokenRepository;
import com.tutti.server.domain.user.dto.response.UserProfileResponse;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
import com.tutti.server.global.auth.jwt.JwtProperties;
import com.tutti.server.global.auth.jwt.JwtTokenProvider;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import com.tutti.server.infra.oauth.GoogleOAuthService;
import com.tutti.server.infra.oauth.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 인증(Authentication) 서비스 — 회원가입, 로그인, 토큰 관리를 담당합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client] → AuthController → <b>AuthService</b> → ProfileRepository, RefreshTokenRepository → DB
 *                                              → JwtTokenProvider (JWT 발급)
 * </pre>
 *
 * <h3>주요 의존성</h3>
 * <ul>
 * <li>{@link ProfileRepository} — 사용자 프로필 CRUD</li>
 * <li>{@link RefreshTokenRepository} — Refresh Token 해시 저장/조회</li>
 * <li>{@link JwtTokenProvider} — Access/Refresh JWT 생성 및 검증</li>
 * <li>{@link PasswordEncoder} — BCrypt 기반 비밀번호 해싱</li>
 * </ul>
 *
 * <h3>보안 설계</h3>
 * <ul>
 * <li>Refresh Token은 원문이 아닌 <b>SHA-256 해시</b>로 DB에 저장합니다.
 * DB 유출 시에도 세션 탈취를 방지하기 위함입니다.</li>
 * <li>토큰 갱신 시 <b>Rotation 전략</b>을 적용합니다.
 * 기존 토큰을 삭제하고 새 토큰을 발급하여, 탈취된 토큰의 재사용을 차단합니다.</li>
 * </ul>
 *
 * @see com.tutti.server.domain.auth.controller.AuthController
 */
@Slf4j
// @Service: Spring이 이 클래스를 "서비스 빈"으로 등록합니다.
// 비즈니스 로직을 담당하는 계층임을 나타내며, Controller에서 주입받아 사용합니다.
@Service
// @RequiredArgsConstructor: final 필드에 대한 생성자를 Lombok이 자동 생성합니다.
// Spring이 생성자 주입(Constructor Injection)으로 의존성을 자동 연결합니다.
// 왜 @Autowired가 아닌가? → 생성자 주입이 불변성과 테스트 용이성 면에서 권장됩니다.
@RequiredArgsConstructor
// @Transactional(readOnly = true): 이 클래스의 모든 메서드는 기본적으로
// 읽기 전용 트랜잭션으로 실행됩니다. 읽기 전용으로 설정하면 Hibernate가
// Dirty Checking(변경 감지)을 생략하여 성능이 향상됩니다.
// 데이터를 변경하는 메서드에는 별도로 @Transactional을 붙여 쓰기 모드로 전환합니다.
@Transactional(readOnly = true)
public class AuthService {

    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    // ══════════════════════════════════════
    // 2.1 회원가입
    // ══════════════════════════════════════

    /**
     * 이메일 기반 회원가입.
     *
     * <p>
     * <b>데이터 흐름:</b>
     * </p>
     * 
     * <pre>
     * Client (SignupRequest) → 이메일 중복 검사 → Profile 생성 → JWT 발급 → AuthResponse
     * </pre>
     *
     * @param request 이메일, 이름, 비밀번호를 담은 가입 요청 DTO
     * @return JWT 토큰과 사용자 프로필을 포함한 응답
     * @throws BusinessException EMAIL_ALREADY_EXISTS — 이메일이 이미 DB에 존재할 때
     */
    // @Transactional: 쓰기 작업이므로 읽기 전용을 오버라이드합니다.
    // 이 메서드 안에서 에러가 발생하면 Profile 저장과 RefreshToken 저장 모두 롤백됩니다.
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 1. 이메일 중복 검사 — 같은 이메일로 이미 가입된 유저가 있으면 409 Conflict
        if (profileRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 2. Profile 엔티티 생성 — 비밀번호는 BCrypt로 해싱하여 저장
        Profile profile = Profile.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(Profile.Provider.EMAIL)
                .build();

        profileRepository.save(profile);

        // 3. JWT Access Token + Refresh Token 발급 → 응답
        return issueTokens(profile);
    }

    // ══════════════════════════════════════
    // 2.2 이메일 중복 확인
    // ══════════════════════════════════════

    /**
     * 이메일 중복 확인 — 가입 폼에서 실시간으로 확인할 때 사용합니다.
     *
     * @param email 확인할 이메일
     * @throws BusinessException EMAIL_ALREADY_EXISTS — 이미 존재하는 이메일
     */
    public void checkEmail(String email) {
        if (profileRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    // ══════════════════════════════════════
    // 2.3 일반 로그인
    // ══════════════════════════════════════

    /**
     * 이메일 + 비밀번호 기반 로그인.
     *
     * <p>
     * <b>보안 고려사항:</b>
     * </p>
     * <ul>
     * <li>이메일이 틀렸을 때와 비밀번호가 틀렸을 때 동일한 에러를 반환합니다.
     * → 공격자가 "이 이메일은 가입되어 있다"는 정보를 얻지 못하게 합니다.</li>
     * <li>비활성화(탈퇴)된 계정은 별도의 에러(ACCOUNT_DISABLED)를 반환합니다.
     * → CS 문의 시 "계정이 비활성화되었습니다"라는 안내를 해줄 수 있습니다.</li>
     * </ul>
     *
     * @param request 이메일과 비밀번호를 담은 로그인 요청
     * @return JWT 토큰과 사용자 프로필
     * @throws BusinessException INVALID_CREDENTIALS — 이메일 또는 비밀번호 불일치
     * @throws BusinessException ACCOUNT_DISABLED — 탈퇴한 계정
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. 이메일로 프로필 조회 — isActive 필터 없이 조회하여 탈퇴 유저를 구분함
        Profile profile = profileRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 탈퇴한 사용자인 경우 → 403 반환
        if (!profile.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 3. 비밀번호 검증 — password가 null(소셜 계정)이면 로그인 불가
        if (profile.getPassword() == null || !passwordEncoder.matches(request.getPassword(), profile.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. JWT 토큰 발급
        return issueTokens(profile);
    }

    // ══════════════════════════════════════
    // 2.4 소셜 로그인
    // ══════════════════════════════════════

    /**
     * OAuth 2.0 기반 소셜 로그인.
     *
     * <p>
     * <b>데이터 흐름:</b>
     * </p>
     * 
     * <pre>
     * Client (인가 코드) → OAuth 서버에서 사용자 정보 교환 → 기존 유저 조회 or 신규 생성 → JWT 발급
     * </pre>
     *
     * <p>
     * 현재 {@code exchangeCodeForEmail()}은 TODO 상태입니다.
     * 프론트엔드에서 Supabase SDK를 통해 인증하는 방식으로 전환 시 이 메서드를 교체합니다.
     * </p>
     *
     * @throws BusinessException UNSUPPORTED_PROVIDER — 지원하지 않는 OAuth 제공자
     * @throws BusinessException OAUTH_SERVER_ERROR — OAuth 서버 통신 실패
     */
    @Transactional
    public AuthResponse socialLogin(SocialLoginRequest request) {
        // 1. provider 문자열을 Enum으로 변환 — 지원하지 않는 제공자면 예외
        Profile.Provider provider = parseProvider(request.getProvider());

        // 2. OAuth 인가 코드로 사용자 정보(이메일, 이름, 아바타) 교환
        String email;
        String name;
        String avatarUrl;

        if (provider == Profile.Provider.GOOGLE) {
            GoogleUserInfo userInfo = googleOAuthService.exchangeCodeForUserInfo(request.getCode(), request.getRedirectUri());
            email = userInfo.getEmail();
            name = userInfo.getName();
            avatarUrl = userInfo.getAvatarUrl();
        } else {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }

        // 3. 기존 활성 유저 조회 → 없으면 신규 프로필 생성
        Profile profile = profileRepository.findByEmail(email)
                .filter(Profile::isActive)
                .orElseGet(() -> {
                    Profile newProfile = Profile.builder()
                            .email(email)
                            .name(name)
                            .provider(provider)
                            .avatarUrl(avatarUrl)
                            .build();
                    return profileRepository.save(newProfile);
                });

        // 4. JWT 토큰 발급
        return issueTokens(profile);
    }

    // ══════════════════════════════════════
    // 2.5 토큰 갱신 (Refresh Token Rotation)
    // ══════════════════════════════════════

    /**
     * Access Token 갱신 — Refresh Token Rotation 전략 적용.
     *
     * <p>
     * <b>Rotation이란?</b>
     * </p>
     * 토큰을 갱신할 때 기존 Refresh Token을 삭제하고 새로운 것을 발급합니다.
     * 만약 공격자가 탈취한 토큰으로 갱신을 시도하면,
     * 정상 사용자가 이미 갱신하여 기존 토큰이 삭제된 상태이므로 실패합니다.
     *
     * <p>
     * <b>데이터 흐름:</b>
     * </p>
     * 
     * <pre>
     * Client (Refresh Token) → SHA-256 해시 → DB 조회 → 만료 확인
     *   → 기존 토큰 삭제 → 새 Access + Refresh Token 발급 → TokenResponse
     * </pre>
     *
     * @param request 갱신할 Refresh Token을 담은 요청
     * @return 새로운 Access Token + Refresh Token
     * @throws BusinessException INVALID_REFRESH_TOKEN — 해시가 DB에 존재하지 않음
     * @throws BusinessException EXPIRED_REFRESH_TOKEN — 만료된 토큰
     * @throws BusinessException USER_NOT_FOUND — 토큰의 유저가 삭제됨
     */
    @Transactional
    public TokenResponse refresh(TokenRefreshRequest request) {
        // 1. Refresh Token → SHA-256 해시 → DB에서 조회
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 2. 만료 확인 — 만료된 토큰은 삭제 후 에러
        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        UUID userId = storedToken.getUserId();

        // 3. Rotation: 기존 토큰을 삭제하여 1회용으로 만듦
        refreshTokenRepository.delete(storedToken);

        // 4. 유저 조회 — 탈퇴한 유저는 토큰 갱신 불가
        Profile profile = profileRepository.findByIdAndIsActive(userId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 5. 새 토큰 쌍 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(profile.getId(), profile.getEmail());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(profile.getId());

        saveRefreshToken(profile.getId(), newRefreshToken);

        return TokenResponse.builder()
                .userId(profile.getId().toString())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    // ══════════════════════════════════════
    // 2.6 로그아웃
    // ══════════════════════════════════════

    /**
     * 로그아웃 — 해당 사용자의 모든 Refresh Token을 삭제합니다.
     * 모든 기기에서 동시에 로그아웃되는 효과가 있습니다.
     *
     * @param userId 로그아웃할 사용자 ID
     */
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // ══════════════════════════════════════
    // 내부 헬퍼 메서드
    // ══════════════════════════════════════

    /**
     * JWT Access Token + Refresh Token 발급 공통 로직.
     * 회원가입/로그인/소셜 로그인에서 공통으로 사용됩니다.
     */
    private AuthResponse issueTokens(Profile profile) {
        String accessToken = jwtTokenProvider.createAccessToken(profile.getId(), profile.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(profile.getId());

        saveRefreshToken(profile.getId(), refreshToken);

        return AuthResponse.builder()
                .user(UserProfileResponse.from(profile))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * Refresh Token을 SHA-256 해시로 변환하여 DB에 저장합니다.
     * 왜 해시로 저장하나? → DB 유출 시 원문 토큰을 복원할 수 없어
     * 공격자가 세션을 탈취할 수 없습니다.
     */
    private void saveRefreshToken(UUID userId, String rawToken) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * 문자열을 SHA-256으로 해싱합니다.
     * Java 표준 라이브러리의 MessageDigest를 사용하며,
     * 결과를 16진수 문자열(64자)로 반환합니다.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에서 지원되므로 이 에러는 발생하지 않습니다.
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /** OAuth 제공자 문자열을 Enum으로 변환. 지원하지 않는 값이면 예외. */
    private Profile.Provider parseProvider(String provider) {
        try {
            return Profile.Provider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
    }

    // ── OAuth 헬퍼 ──

    private final GoogleOAuthService googleOAuthService;


}
