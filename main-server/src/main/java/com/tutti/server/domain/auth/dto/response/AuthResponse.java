package com.tutti.server.domain.auth.dto.response;

import com.tutti.server.domain.user.dto.response.UserProfileResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    private UserProfileResponse user;
    private String accessToken;
    private String refreshToken;
}
