package com.tutti.server.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {

    @NotBlank(message = "OAuth 제공자는 필수입니다.")
    private String provider;

    @NotBlank(message = "인증 코드는 필수입니다.")
    private String code;
}
