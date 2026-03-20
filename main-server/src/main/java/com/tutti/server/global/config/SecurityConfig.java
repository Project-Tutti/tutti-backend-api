package com.tutti.server.global.config;

import com.tutti.server.global.auth.jwt.JwtAuthenticationFilter;
import com.tutti.server.global.auth.jwt.JwtTokenProvider;
import com.tutti.server.global.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtTokenProvider jwtTokenProvider;
        private final ObjectMapper objectMapper;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)

                                // ERR-4 FIX: Security 예외를 API 형식으로 반환
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response,
                                                                authException) -> writeErrorResponse(response,
                                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "UNAUTHORIZED", "인증이 필요합니다."))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> writeErrorResponse(response,
                                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                                "ACCESS_DENIED", "접근 권한이 없습니다.")))

                                .authorizeHttpRequests(auth -> auth
                                                // ── 공개 엔드포인트 ──
                                                .requestMatchers(
                                                                "/api/auth/signup",
                                                                "/api/auth/login",
                                                                "/api/auth/social",
                                                                "/api/auth/refresh",
                                                                "/api/auth/check-email",
                                                                "/api/instruments/**")
                                                .permitAll()
                                                // ── Actuator Health ──
                                                .requestMatchers("/actuator/health").permitAll()
                                                // ── Swagger UI ──
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/swagger-resources/**",
                                                                "/v3/api-docs/**",
                                                                "/webjars/**")
                                                .permitAll()
                                                // SEC-2 FIX: 정확한 내부 콜백 경로만 허용 (와일드카드 제거)
                                                .requestMatchers("/internal/callback/arrange").permitAll()
                                                // ── 나머지는 인증 필요 ──
                                                .anyRequest().authenticated())
                                .addFilterBefore(
                                                new JwtAuthenticationFilter(jwtTokenProvider),
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOriginPatterns(List.of("*"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        /**
         * ERR-4 FIX: Security 예외 시 API 명세 형식의 JSON 에러 응답 반환.
         */
        private void writeErrorResponse(HttpServletResponse response, int status,
                        String errorCode, String message) throws IOException {
                response.setStatus(status);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ApiResponse<Map<String, String>> body = ApiResponse.error(message,
                                Map.of("errorCode", errorCode, "details", message));

                objectMapper.writeValue(response.getOutputStream(), body);
        }
}
