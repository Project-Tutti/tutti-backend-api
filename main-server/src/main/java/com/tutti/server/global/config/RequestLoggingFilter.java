package com.tutti.server.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 요청/응답 로깅 필터.
 * 모든 API 요청에 대해 아래 정보를 구조화된 로그로 남깁니다:
 * - 누가 (userId 또는 anonymous)
 * - 어떤 API (METHOD + URI)
 * - 결과 (HTTP 상태 코드)
 * - 소요 시간 (ms)
 *
 * Grafana Loki에서 이 로그를 조회하여 대시보드에 표시합니다.
 * Actuator, Swagger 등 내부 경로는 제외합니다.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Actuator, Swagger, 정적 리소스는 로깅 제외
        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String method = request.getMethod();
            String user = extractUser();
            String clientIp = getClientIp(request);
            String queryString = request.getQueryString();
            String fullUri = queryString != null ? uri + "?" + queryString : uri;

            if (status >= 400) {
                log.warn("HTTP {} {} — user={} ip={} — {} — {}ms",
                        method, fullUri, user, clientIp, status, duration);
            } else {
                log.info("HTTP {} {} — user={} ip={} — {} — {}ms",
                        method, fullUri, user, clientIp, status, duration);
            }
        }
    }

    private String extractUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName(); // UUID
        }
        return "anonymous";
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean shouldSkip(String uri) {
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars")
                || uri.equals("/favicon.ico");
    }
}
