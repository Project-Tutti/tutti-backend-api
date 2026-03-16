package com.tutti.server.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 설정 — JWT Bearer 인증을 Swagger UI에 추가합니다.
 *
 * <p>
 * 설정 후 Swagger UI 상단에 "Authorize 🔒" 버튼이 표시되며,
 * Access Token을 입력하면 인증이 필요한 API를 바로 테스트할 수 있습니다.
 * </p>
 *
 * <h3>사용 방법</h3>
 * <ol>
 * <li>로그인 API로 Access Token 발급</li>
 * <li>Swagger UI 상단 "Authorize" 클릭</li>
 * <li>Value에 Access Token 입력 (Bearer 접두사 불필요)</li>
 * <li>인증 필요 API 테스트</li>
 * </ol>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Tutti API")
                        .version("2.0")
                        .description("Tutti Backend API — 인증이 필요한 API는 🔒 Authorize 버튼으로 토큰을 설정하세요."))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("로그인 후 발급받은 Access Token을 입력하세요. (Bearer 접두사 불필요)")));
    }
}
