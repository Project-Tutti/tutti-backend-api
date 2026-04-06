package com.tutti.server.domain.project.entity;

/**
 * AI 음악 생성에서 사용 가능한 장르 목록.
 *
 * <p>
 * AI 서버의 vocabulary와 1:1 대응됩니다.
 * (예: {@code CLASSICAL} → 토크나이저에서 {@code GENRE_CLASSICAL} 토큰으로 변환)
 * </p>
 *
 * <p>
 * DB에는 {@code @Enumerated(EnumType.STRING)}으로 문자열 저장됩니다.
 * 프론트엔드에서 String으로 전달받아 {@code Genre.valueOf()} 로 변환합니다.
 * </p>
 */
public enum Genre {
    CLASSICAL,
    JAZZ,
    POP,
    ROCK,
    ELECTRONIC,
    FOLK,
    UNKNOWN
}
