package com.tutti.server.global.auth;

import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * ERR-2 FIX: 인증 정보에서 UUID를 추출하는 유틸리티.
 * 모든 Controller에서 반복되는 UUID.fromString(authentication.getName())를 대체합니다.
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    public static UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "잘못된 사용자 식별자입니다.");
        }
    }
}
