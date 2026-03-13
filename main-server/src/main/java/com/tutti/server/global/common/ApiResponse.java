package com.tutti.server.global.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;

/**
 * 공통 API 응답 래퍼.
 *
 * 모든 API 응답은 아래 형식을 따릅니다:
 * {
 * "isSuccess": true,
 * "message": "요청이 성공하였습니다.",
 * "result": { ... }
 * }
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final boolean isSuccess;
    private final String message;
    private final T result;

    // ── 성공 응답 ──

    public static <T> ApiResponse<T> success(String message, T result) {
        return new ApiResponse<>(true, message, result);
    }

    /**
     * result 없이 성공 응답. 명세서에 맞추어 result: {} 를 반환합니다.
     */
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, (T) Collections.emptyMap());
    }

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(true, "요청이 성공하였습니다.", result);
    }

    // ── 실패 응답 ──

    public static <T> ApiResponse<T> error(String message, T result) {
        return new ApiResponse<>(false, message, result);
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, (T) Collections.emptyMap());
    }
}
