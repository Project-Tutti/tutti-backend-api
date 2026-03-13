package com.tutti.server.domain.user.service;

import com.tutti.server.domain.auth.repository.RefreshTokenRepository;
import com.tutti.server.domain.user.dto.response.UserProfileResponse;
import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.domain.user.repository.ProfileRepository;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // ── 2.7 내 프로필 조회 ──

    public UserProfileResponse getProfile(UUID userId) {
        Profile profile = findActiveProfile(userId);
        return UserProfileResponse.from(profile);
    }

    // ── 2.8 회원 탈퇴 (Soft Delete) ──

    @Transactional
    public void deleteAccount(UUID userId) {
        Profile profile = findActiveProfile(userId);
        profile.softDelete();
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // ── 내부 헬퍼 ──

    private Profile findActiveProfile(UUID userId) {
        return profileRepository.findByIdAndIsActive(userId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
