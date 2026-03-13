package com.tutti.server.domain.user.service;

import com.tutti.server.domain.auth.repository.RefreshTokenRepository;
import com.tutti.server.domain.user.dto.response.UserProfileResponse;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    // ══════════════════════════════════════
    // 2.7 프로필 조회
    // ══════════════════════════════════════

    @Nested
    @DisplayName("getProfile()")
    class GetProfile {

        @Test
        @DisplayName("활성 사용자 — 프로필 정보를 반환한다")
        void 정상_프로필조회() {
            // given
            Profile profile = TestFixtures.createActiveProfile();
            given(profileRepository.findByIdAndIsActive(TestFixtures.USER_ID, true))
                    .willReturn(Optional.of(profile));

            // when
            UserProfileResponse result = userService.getProfile(TestFixtures.USER_ID);

            // then
            assertThat(result.getEmail()).isEqualTo(TestFixtures.EMAIL);
            assertThat(result.getName()).isEqualTo(TestFixtures.NAME);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 — USER_NOT_FOUND 예외")
        void 존재하지않는_사용자() {
            // given
            given(profileRepository.findByIdAndIsActive(TestFixtures.USER_ID, true))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getProfile(TestFixtures.USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════
    // 2.8 회원 탈퇴
    // ══════════════════════════════════════

    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccount {

        @Test
        @DisplayName("정상 탈퇴 — Soft Delete + Refresh Token 전체 삭제")
        void 정상_탈퇴() {
            // given
            Profile profile = TestFixtures.createActiveProfile();
            given(profileRepository.findByIdAndIsActive(TestFixtures.USER_ID, true))
                    .willReturn(Optional.of(profile));

            // when
            userService.deleteAccount(TestFixtures.USER_ID);

            // then
            assertThat(profile.isActive()).isFalse();
            assertThat(profile.getDeletedAt()).isNotNull();
            verify(refreshTokenRepository).deleteAllByUserId(TestFixtures.USER_ID);
        }

        @Test
        @DisplayName("이미 탈퇴한 사용자 — USER_NOT_FOUND 예외")
        void 이미_탈퇴한_사용자() {
            // given
            given(profileRepository.findByIdAndIsActive(TestFixtures.USER_ID, true))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.deleteAccount(TestFixtures.USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
