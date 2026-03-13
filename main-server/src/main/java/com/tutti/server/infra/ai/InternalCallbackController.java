package com.tutti.server.infra.ai;

import com.tutti.server.domain.project.service.ArrangementService;
import com.tutti.server.global.common.ApiResponse;
import com.tutti.server.infra.ai.dto.AiCallbackPayload;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AI 서버에서 편곡 처리 완료 후 호출하는 내부 콜백 엔드포인트.
 * SecurityConfig에서 /internal/** 경로는 인증 없이 접근 가능하도록 설정됨.
 * EDGE-1: 공유 비밀키(X-Callback-Secret)를 통한 인증을 수행합니다.
 */
@Slf4j
@Hidden // Swagger에서 숨김
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCallbackController {

        private final ArrangementService arrangementService;

        @PostMapping("/callback/arrange")
        public ResponseEntity<ApiResponse<Void>> handleArrangeCallback(
                        @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
                        @RequestBody AiCallbackPayload payload) {

                // EDGE-1: 콜백 인증 — 공유 비밀키를 상수 시간(constant-time)으로 검증
                // MessageDigest.isEqual()은 타이밍 공격(timing attack)을 방지합니다.
                // String.equals()는 첫 불일치 바이트에서 즉시 리턴하므로, 공격자가
                // 응답 시간 차이를 이용해 시크릿을 한 바이트씩 추론할 수 있습니다.
                if (secret == null || !MessageDigest.isEqual(
                                secret.getBytes(StandardCharsets.UTF_8),
                                arrangementService.getCallbackSecret().getBytes(StandardCharsets.UTF_8))) {
                        log.warn("콜백 인증 실패: 잘못된 비밀키, projectId={}, versionId={}",
                                        payload.getProjectId(), payload.getVersionId());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.error("콜백 인증에 실패했습니다."));
                }

                log.info("편곡 콜백 수신: projectId={}, versionId={}, status={}",
                                payload.getProjectId(), payload.getVersionId(), payload.getStatus());

                arrangementService.handleCallback(payload);

                return ResponseEntity.ok(ApiResponse.success("콜백 처리 완료"));
        }
}
