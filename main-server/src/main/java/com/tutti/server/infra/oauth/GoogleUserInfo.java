package com.tutti.server.infra.oauth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Google OAuth에서 추출한 사용자 정보.
 */
@Getter
@AllArgsConstructor
public class GoogleUserInfo {
    private final String email;
    private final String name;
    private final String avatarUrl;
}
