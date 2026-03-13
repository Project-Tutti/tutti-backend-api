package com.tutti.server.global.error;

import lombok.Getter;

/**
 * 비즈니스 예외 (도메인 로직에서 발생).
 * ErrorCode를 포함하여 GlobalExceptionHandler에서 일관된 에러 응답을 반환합니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
