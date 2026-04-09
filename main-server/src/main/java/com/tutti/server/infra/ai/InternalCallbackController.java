package com.tutti.server.infra.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutti.server.domain.project.service.ArrangementService;
import com.tutti.server.global.common.ApiResponse;
import com.tutti.server.infra.ai.dto.AiCallbackPayload;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AI 서버에서 편곡 처리 상태를 전달하는 내부 콜백 엔드포인트.
 * SecurityConfig에서 /internal/** 경로는 인증 없이 접근 가능하도록 설정됨.
 *
 * <h3>콜백 종류</h3>
 * <ul>
 * <li><b>진행률/실패</b> — {@code application/json} (기존과 동일)</li>
 * <li><b>완료</b> — {@code multipart/form-data} (metadata JSON + MIDI file)</li>
 * </ul>
 *
 * <p>
 * 온프레미스 AI 서버에서 편곡 결과를 안정적으로 전달하기 위해,
 * 완료 시 MIDI 파일을 직접 multipart로 전송합니다.
 * </p>
 */
@Slf4j
@Hidden // Swagger에서 숨김
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCallbackController {

        private final ArrangementService arrangementService;
        private final ObjectMapper objectMapper;

        // ── 완료 콜백: Multipart (MIDI 파일 포함) ──

        /**
         * AI 서버가 편곡 완료 시 MIDI 파일과 함께 호출하는 콜백.
         *
         * @param secret       X-Callback-Secret 헤더 (인증용)
         * @param metadataJson "metadata" 파트: AiCallbackPayload JSON 문자열
         * @param file         "file" 파트: 편곡 결과 MIDI 파일
         */
        @PostMapping(value = "/callback/arrange", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<Void>> handleArrangeCallbackWithFile(
                        @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
                        @RequestPart("metadata") String metadataJson,
                        @RequestPart("file") MultipartFile file) {

                // 1. 시크릿 검증
                if (!verifySecret(secret)) {
                        log.warn("콜백 인증 실패 (multipart): 잘못된 비밀키");
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.error("콜백 인증에 실패했습니다."));
                }

                try {
                        // 2. metadata JSON 파싱
                        AiCallbackPayload payload = objectMapper.readValue(metadataJson, AiCallbackPayload.class);

                        log.info("편곡 완료 콜백 수신 (multipart): projectId={}, versionId={}, fileSize={}",
                                        payload.getProjectId(), payload.getVersionId(), file.getSize());

                        // 3. 파일 포함 콜백 처리
                        arrangementService.handleCallbackWithFile(payload, file.getBytes());

                        return ResponseEntity.ok(ApiResponse.success("콜백 처리 완료"));
                } catch (Exception e) {
                        log.error("Multipart 콜백 처리 실패: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("콜백 처리 중 오류가 발생했습니다."));
                }
        }

        // ── 진행률/실패 콜백: JSON ──

        /**
         * AI 서버가 진행률 업데이트 또는 실패 시 호출하는 콜백.
         * Content-Type: application/json
         */
        @PostMapping(value = "/callback/arrange", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ApiResponse<Void>> handleArrangeCallback(
                        @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
                        @RequestBody AiCallbackPayload payload) {

                if (!verifySecret(secret)) {
                        log.warn("콜백 인증 실패: 잘못된 비밀키, projectId={}, versionId={}",
                                        payload.getProjectId(), payload.getVersionId());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.error("콜백 인증에 실패했습니다."));
                }

                log.info("편곡 콜백 수신 (JSON): projectId={}, versionId={}, status={}",
                                payload.getProjectId(), payload.getVersionId(), payload.getStatus());

                arrangementService.handleCallback(payload);

                return ResponseEntity.ok(ApiResponse.success("콜백 처리 완료"));
        }

        // ── 공통: 시크릿 검증 ──

        /**
         * 콜백 시크릿을 상수 시간(constant-time)으로 검증합니다.
         * MessageDigest.isEqual()은 타이밍 공격(timing attack)을 방지합니다.
         */
        private boolean verifySecret(String secret) {
                if (secret == null) {
                        return false;
                }
                return MessageDigest.isEqual(
                                secret.getBytes(StandardCharsets.UTF_8),
                                arrangementService.getCallbackSecret().getBytes(StandardCharsets.UTF_8));
        }
}
