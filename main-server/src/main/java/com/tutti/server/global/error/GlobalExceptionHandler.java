package com.tutti.server.global.error;

import com.tutti.server.global.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * 전역 예외 처리기 — 모든 Controller에서 발생하는 예외를 일관된 형식으로 변환합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * Controller에서 예외 발생 → Spring이 이 핸들러를 자동 호출 → 표준화된 JSON 에러 응답
 * </pre>
 *
 * <h3>처리하는 예외 유형</h3>
 * <ol>
 * <li>{@link BusinessException} — 비즈니스 로직 예외 (ErrorCode 포함)</li>
 * <li>{@link MethodArgumentNotValidException} — @Valid 검증 실패 (DTO 필드 검증)</li>
 * <li>{@link ConstraintViolationException} — @PathVariable, @RequestParam 검증
 * 실패</li>
 * <li>{@link MaxUploadSizeExceededException} — 파일 업로드 크기 초과</li>
 * <li>{@link Exception} — 예상치 못한 모든 예외 (500 Internal Error)</li>
 * </ol>
 *
 * <h3>응답 형식 (API 명세서 표준)</h3>
 * 
 * <pre>
 * {
 *   "isSuccess": false,
 *   "message": "에러 메시지",
 *   "result": {
 *     "errorCode": "ERROR_CODE_NAME",
 *     "details": "상세 설명"
 *   }
 * }
 * </pre>
 *
 * @RestControllerAdvice: @ControllerAdvice + @ResponseBody를 합친 것으로,
 *                        모든 Controller에서 발생하는 예외를 한 곳에서 잡아 JSON으로 변환합니다.
 *                        이 어노테이션이 없으면 각 Controller마다 try-catch를 작성해야 합니다.
 *
 * @see BusinessException
 * @see ErrorCode
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * 비즈니스 예외 처리 — 도메인 로직에서 의도적으로 던진 예외.
         * 예: 이메일 중복, 프로젝트 없음, 권한 없음 등.
         *
         * @ExceptionHandler: Spring에게 "이 타입의 예외가 발생하면 이 메서드를 호출해"라고 알려줍니다.
         */
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleBusinessException(BusinessException e) {
                ErrorCode errorCode = e.getErrorCode();
                log.warn("BusinessException: {} - {}", errorCode.name(), e.getMessage());

                Map<String, String> errorResult = Map.of(
                                "errorCode", errorCode.name(),
                                "details", e.getMessage());

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.error(errorCode.getMessage(), errorResult));
        }

        /**
         * @Valid 검증 실패 — DTO의 @NotBlank, @Email 등 어노테이션 검증 실패.
         *        여러 필드의 에러를 하나의 문자열로 합쳐서 반환합니다.
         *        예: "email: 이메일은 필수입니다., password: 비밀번호는 8~20자 이내여야 합니다."
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
                        MethodArgumentNotValidException e) {
                String detail = e.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("유효성 검사 실패");

                Map<String, String> errorResult = Map.of(
                                "errorCode", ErrorCode.INVALID_INPUT.name(),
                                "details", detail);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getMessage(), errorResult));
        }

        /**
         * @PathVariable/@RequestParam 제약 조건 위반.
         *                             예: @Min(1) 위반, @Pattern 불일치 등.
         */
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
                        ConstraintViolationException e) {
                Map<String, String> errorResult = Map.of(
                                "errorCode", ErrorCode.INVALID_INPUT.name(),
                                "details", e.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getMessage(), errorResult));
        }

        /**
         * 파일 업로드 크기 초과 — application.yml의 max-file-size를 넘는 경우.
         * Spring이 자동으로 이 예외를 던집니다.
         */
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleMaxUploadSizeExceeded(
                        MaxUploadSizeExceededException e) {
                Map<String, String> errorResult = Map.of(
                                "errorCode", ErrorCode.FILE_TOO_LARGE.name(),
                                "details", "최대 파일 크기: 10MB");

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.getMessage(), errorResult));
        }

        /**
         * @RequestParam/@PathVariable의 @Email, @Min 등 검증 실패.
         *                              Spring 6.1+에서 ConstraintViolationException 대신 이
         *                              예외를 던집니다.
         */
        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodValidation(
                        HandlerMethodValidationException e) {
                String detail = e.getAllErrors().stream()
                                .map(error -> error.getDefaultMessage())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("유효성 검사 실패");

                Map<String, String> errorResult = Map.of(
                                "errorCode", ErrorCode.INVALID_INPUT.name(),
                                "details", detail);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getMessage(), errorResult));
        }

        /**
         * HTTP 메서드 불일치 — 예: POST 전용 엔드포인트에 GET 요청.
         * 405 Method Not Allowed를 반환하며, 지원하는 메서드를 안내합니다.
         */
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodNotAllowed(
                        HttpRequestMethodNotSupportedException e) {
                String supported = e.getSupportedHttpMethods() != null
                                ? e.getSupportedHttpMethods().toString()
                                : "확인 불가";

                Map<String, String> errorResult = Map.of(
                                "errorCode", "METHOD_NOT_ALLOWED",
                                "details", "지원하지 않는 HTTP 메서드입니다. 지원: " + supported);

                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(ApiResponse.error("지원하지 않는 HTTP 메서드입니다.", errorResult));
        }

        /**
         * 예상치 못한 모든 예외의 최종 방어선 (catch-all).
         *
         * <p>
         * <b>보안 주의:</b> 내부 에러 상세(스택 트레이스 등)를 클라이언트에 노출하지 않습니다.
         * 서버 로그에만 에러를 기록하고, 클라이언트에는 일반적인 메시지만 반환합니다.
         * </p>
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleUnexpectedException(Exception e) {
                log.error("Unexpected error occurred", e);

                Map<String, String> errorResult = Map.of(
                                "errorCode", ErrorCode.INTERNAL_ERROR.name(),
                                "details", "예기치 않은 서버 오류가 발생하였습니다. Error: " + e.getMessage() + " (" + e.getClass().getSimpleName() + ")");

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getMessage(), errorResult));
        }
}
