package com.tutti.server.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenResponse {

    private String userId;
    private String accessToken;
    private String refreshToken;
}
