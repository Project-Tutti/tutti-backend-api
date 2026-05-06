package com.tutti.server.global.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 직렬화 설정.
 *
 * <p>
 * {@code LocalDateTime}은 기본적으로 타임존 정보 없이 직렬화됩니다.
 * JVM 타임존이 UTC로 고정되어 있으므로({@code -Duser.timezone=UTC}),
 * 모든 {@code LocalDateTime} 값은 UTC 시각임이 보장됩니다.
 * 이 설정은 직렬화 시 {@code Z} 접미사를 붙여 프론트엔드가
 * UTC임을 명확히 인식할 수 있도록 합니다.
 * </p>
 *
 * <pre>
 * 변환 전: "2026-05-06T06:53:00"     ← UTC인지 KST인지 알 수 없음
 * 변환 후: "2026-05-06T06:53:00Z"    ← UTC임이 명확
 * </pre>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeUtcCustomizer() {
        return builder -> builder.serializers(
                new LocalDateTimeSerializer(UTC_FORMATTER)
        );
    }
}
