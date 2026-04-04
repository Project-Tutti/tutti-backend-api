package com.tutti.server.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@NoArgsConstructor
@Schema(description = "소셜 로그인 요청 DTO")
public class SocialLoginRequest {

    @Schema(description = "OAuth 제공자 (예: google)", example = "google")
    @NotBlank(message = "OAuth 제공자는 필수입니다.")
    private String provider;

    @Schema(description = "인가 코드 (Authorization Code) 또는 ID Token", example = "4/0Aci98E8...")
    @NotBlank(message = "인증 코드는 필수입니다.")
    private String code;

    @Schema(description = "인가 코드를 받을 때 사용된 리다이렉트 URI (프론트엔드 환경 호환을 위해 필수)", example = "http://localhost:3000/auth/callback")
    @NotBlank(message = "리다이렉트 URI는 필수입니다.")
    private String redirectUri;
}
