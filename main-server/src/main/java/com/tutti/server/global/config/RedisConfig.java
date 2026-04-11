package com.tutti.server.global.config;

import com.tutti.server.infra.redis.SseRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 설정 — 요청 큐잉 + SSE 멀티 Pod 동기화.
 *
 * <h3>등록되는 Bean 2개</h3>
 * <ol>
 * <li>{@code stringRedisTemplate} — AI 요청 스트림(XADD) + SSE 이벤트 발행(PUBLISH)</li>
 * <li>{@code redisMessageListenerContainer} — SSE 이벤트 구독(SUBSCRIBE)</li>
 * </ol>
 *
 * <h3>Redis 채널</h3>
 * <ul>
 * <li>{@code "tutti:sse:progress"} — SSE 진행률 이벤트 Pub/Sub 채널</li>
 * <li>{@code "ai:arrange:stream"} — AI 편곡 요청 스트림 (Redis Streams + Consumer Group)</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * AI 요청 큐잉 + SSE Pub/Sub 발행용 StringRedisTemplate.
     * JSON 문자열을 직접 다루므로 String 직렬화 사용.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * SSE Pub/Sub 구독용 MessageListenerContainer.
     * 모든 Pod가 "tutti:sse:progress" 채널을 구독하여 브로드캐스트를 수신합니다.
     *
     * <p>
     * 동작 원리: Redis PUBLISH → 이 Container가 메시지 수신 →
     * SseRedisSubscriber.onMessage() 호출 → 로컬 emitter에 SSE 전달
     * </p>
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic("tutti:sse:progress"));
        return container;
    }
}
