package com.tutti.server.domain.auth.service;

import com.tutti.server.domain.auth.dto.request.LoginRequest;
import com.tutti.server.domain.auth.dto.request.SignupRequest;
import com.tutti.server.domain.auth.dto.request.TokenRefreshRequest;
import com.tutti.server.domain.auth.entity.RefreshToken;
import com.tutti.server.domain.auth.repository.RefreshTokenRepository;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
import com.tutti.server.global.auth.jwt.JwtProperties;
import com.tutti.server.global.auth.jwt.JwtTokenProvider;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import com.tutti.server.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private PasswordEncoder passwordEncoder;

    // ══════════════════════════════════════
    // 2.1 회원가입
    // ══════════════════════════════════════

    @Nested
    @DisplayName("signup()")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 시 토큰이 발급된다")
        void 정상_회원가입_토큰발급() {
            // given
            SignupRequest request = createSignupRequest("new@tutti.com", "테스트", "Pass123!@");
            given(profileRepository.existsByEmail("new@tutti.com")).willReturn(false);
            given(passwordEncoder.encode("Pass123!@")).willReturn("encodedPass");
            given(profileRepository.save(any(Profile.class)))
                    .willAnswer(inv -> {
                        Profile p = inv.getArgument(0);
                        ReflectionTestUtils.setField(p, "id", TestFixtures.USER_ID);
                        return p;
                    });
            given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("refresh-token");
            given(jwtProperties.getRefreshTokenExpiration()).willReturn(1209600000L);

            // when
            var result = authService.signup(request);

            // then
            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(result.getUser()).isNotNull();
            verify(profileRepository).save(any(Profile.class));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("이미 존재하는 이메일로 가입 시 EMAIL_ALREADY_EXISTS 예외")
        void 중복이메일_예외() {
            // given
            SignupRequest request = createSignupRequest("dup@tutti.com", "테스트", "Pass123!@");
            given(profileRepository.existsByEmail("dup@tutti.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    // ══════════════════════════════════════
    // 2.2 이메일 중복 확인
    // ══════════════════════════════════════

    @Nested
    @DisplayName("checkEmail()")
    class CheckEmail {

        @Test
        @DisplayName("사용 가능한 이메일 — 예외 없이 통과한다")
        void 사용가능_이메일() {
            // given
            given(profileRepository.existsByEmail("ok@tutti.com")).willReturn(false);

            // when & then — 예외 없이 정상 완료
            authService.checkEmail("ok@tutti.com");
        }

        @Test
        @DisplayName("이미 존재하는 이메일 — EMAIL_ALREADY_EXISTS 예외")
        void 중복이메일_예외() {
            // given
            given(profileRepository.existsByEmail("dup@tutti.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.checkEmail("dup@tutti.com"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    // ══════════════════════════════════════
    // 2.3 일반 로그인
    // ══════════════════════════════════════

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("정상 로그인 — 토큰 발급 성공")
        void 정상_로그인() {
            // given
            Profile profile = TestFixtures.createActiveProfile();
            LoginRequest request = createLoginRequest(TestFixtures.EMAIL, TestFixtures.RAW_PASSWORD);

            given(profileRepository.findByEmail(TestFixtures.EMAIL)).willReturn(Optional.of(profile));
            given(passwordEncoder.matches(TestFixtures.RAW_PASSWORD, profile.getPassword())).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("access");
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("refresh");
            given(jwtProperties.getRefreshTokenExpiration()).willReturn(1209600000L);

            // when
            var result = authService.login(request);

            // then
            assertThat(result.getAccessToken()).isEqualTo("access");
            assertThat(result.getRefreshToken()).isEqualTo("refresh");
        }

        @Test
        @DisplayName("존재하지 않는 이메일 — INVALID_CREDENTIALS 예외")
        void 존재하지않는_이메일() {
            // given
            LoginRequest request = createLoginRequest("none@tutti.com", "somePass");
            given(profileRepository.findByEmail("none@tutti.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("비활성화된 계정 — ACCOUNT_DISABLED 예외")
        void 비활성화_계정() {
            // given
            Profile deactivated = TestFixtures.createDeactivatedProfile();
            LoginRequest request = createLoginRequest(TestFixtures.EMAIL, TestFixtures.RAW_PASSWORD);
            given(profileRepository.findByEmail(TestFixtures.EMAIL)).willReturn(Optional.of(deactivated));

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        }

        @Test
        @DisplayName("비밀번호 불일치 — INVALID_CREDENTIALS 예외")
        void 비밀번호_불일치() {
            // given
            Profile profile = TestFixtures.createActiveProfile();
            LoginRequest request = createLoginRequest(TestFixtures.EMAIL, "wrongPassword");
            given(profileRepository.findByEmail(TestFixtures.EMAIL)).willReturn(Optional.of(profile));
            given(passwordEncoder.matches("wrongPassword", profile.getPassword())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("password가 null인 소셜 계정 — INVALID_CREDENTIALS 예외")
        void 소셜계정_비밀번호null() {
            // given
            Profile socialProfile = Profile.builder()
                    .email("social@tutti.com")
                    .name("소셜유저")
                    .provider(Profile.Provider.GOOGLE)
                    .build();
            ReflectionTestUtils.setField(socialProfile, "id", TestFixtures.USER_ID);

            LoginRequest request = createLoginRequest("social@tutti.com", "anyPass");
            given(profileRepository.findByEmail("social@tutti.com")).willReturn(Optional.of(socialProfile));

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // ══════════════════════════════════════
    // 2.5 토큰 갱신
    // ══════════════════════════════════════

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("정상 토큰 갱신 — Rotation 적용")
        void 정상_토큰갱신() {
            // given
            TokenRefreshRequest request = createRefreshRequest("raw-refresh-token");
            RefreshToken validToken = TestFixtures.createValidRefreshToken(
                    TestFixtures.USER_ID, anyHashOf("raw-refresh-token"));
            Profile profile = TestFixtures.createActiveProfile();

            given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(validToken));
            given(profileRepository.findByIdAndIsActive(TestFixtures.USER_ID, true))
                    .willReturn(Optional.of(profile));
            given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("new-access");
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("new-refresh");
            given(jwtProperties.getRefreshTokenExpiration()).willReturn(1209600000L);

            // when
            var result = authService.refresh(request);

            // then
            assertThat(result.getAccessToken()).isEqualTo("new-access");
            assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
            verify(refreshTokenRepository).delete(validToken); // Rotation: 기존 삭제
            verify(refreshTokenRepository).save(any(RefreshToken.class)); // 새 토큰 저장
        }

        @Test
        @DisplayName("존재하지 않는 Refresh Token — INVALID_REFRESH_TOKEN 예외")
        void 존재하지않는_토큰() {
            // given
            TokenRefreshRequest request = createRefreshRequest("invalid-token");
            given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("만료된 Refresh Token — EXPIRED_REFRESH_TOKEN 예외")
        void 만료된_토큰() {
            // given
            TokenRefreshRequest request = createRefreshRequest("expired-token");
            RefreshToken expiredToken = TestFixtures.createExpiredRefreshToken(
                    TestFixtures.USER_ID, "hash");
            given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expiredToken));

            // when & then
            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EXPIRED_REFRESH_TOKEN);
            verify(refreshTokenRepository).delete(expiredToken); // 만료 토큰 삭제 확인
        }
    }

    // ══════════════════════════════════════
    // 2.6 로그아웃
    // ══════════════════════════════════════

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("정상 로그아웃 — 해당 사용자 Refresh Token 전체 삭제")
        void 정상_로그아웃() {
            // when
            authService.logout(TestFixtures.USER_ID);

            // then
            verify(refreshTokenRepository).deleteAllByUserId(TestFixtures.USER_ID);
        }
    }

    // ══════════════════════════════════════
    // Helper: DTO 생성 (Reflection으로 NoArgsConstructor DTO 필드 주입)
    // ══════════════════════════════════════

    private SignupRequest createSignupRequest(String email, String name, String password) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "name", name);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private LoginRequest createLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private TokenRefreshRequest createRefreshRequest(String refreshToken) {
        TokenRefreshRequest req = new TokenRefreshRequest();
        ReflectionTestUtils.setField(req, "refreshToken", refreshToken);
        return req;
    }

    private String anyHashOf(String token) {
        return "dummy-hash"; // Mock은 anyString()으로 매칭
    }
}
