package com.tutti.server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서 기반 에러 코드 정의.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생하였습니다."),

    // ── Auth ──
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "올바른 이메일 형식이 아닙니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다."),
    INVALID_OAUTH_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 인증 코드입니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 OAuth 제공자입니다."),
    OAUTH_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "소셜 로그인 서버 오류가 발생했습니다."),

    // ── User ──
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // ── Project ──
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 제한을 초과했습니다."),
    INVALID_MIDI_STRUCTURE(HttpStatus.UNPROCESSABLE_ENTITY, "MIDI 파일 구조가 올바르지 않습니다."),
    UNSUPPORTED_INSTRUMENT(HttpStatus.BAD_REQUEST, "AI 편곡을 지원하지 않는 악기입니다."),
    INVALID_INSTRUMENT_CATEGORY(HttpStatus.BAD_REQUEST, "존재하지 않는 악기 카테고리입니다."),
    INVALID_GENRE(HttpStatus.BAD_REQUEST, "지원하지 않는 장르입니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "이름이 올바르지 않습니다."),

    // ── Version ──
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "버전을 찾을 수 없습니다."),
    CANNOT_DELETE_LAST_VERSION(HttpStatus.BAD_REQUEST, "마지막 버전은 삭제할 수 없습니다."),
    PROCESSING_NOT_COMPLETE(HttpStatus.CONFLICT, "아직 처리가 완료되지 않았습니다."),

    // ── File ──
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),

    // ── Library ──
    INVALID_SORT_PARAMETER(HttpStatus.BAD_REQUEST, "올바르지 않은 정렬 파라미터입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
