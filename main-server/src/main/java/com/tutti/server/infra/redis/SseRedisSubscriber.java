package com.tutti.server.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutti.server.domain.project.dto.response.ProgressEvent;
import com.tutti.server.domain.project.service.ArrangementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 구독자 — SSE 이벤트를 로컬 Pod의 emitter로 전달합니다.
 *
 * <h3>왜 필요한가?</h3>
 * 멀티 Pod 환경에서 AI 콜백을 수신한 Pod와 SSE 연결을 보유한 Pod가
 * 다를 수 있습니다. 이 구독자가 모든 Pod에서 실행되며 Redis 채널
 * "tutti:sse:progress"의 메시지를 수신합니다.
 *
 * <h3>동작 흐름</h3>
 * <pre>
 * Redis PUBLISH "tutti:sse:progress" → 이 리스너 (모든 Pod에서 실행)
 *   → deliverSseEventLocally() 호출
 *   → 이 Pod에 emitter가 있으면 SSE 전송 ✅
 *   → 이 Pod에 emitter가 없으면 무시 (no-op) ✅
 * </pre>
 */
@Slf4j
@Component
public class SseRedisSubscriber implements MessageListener {

    private final ArrangementService arrangementService;
    private final ObjectMapper objectMapper;

    /**
     * 순환 의존성 방지를 위해 @Lazy 사용.
     * RedisConfig → SseRedisSubscriber → ArrangementService → StringRedisTemplate ← RedisConfig
     * @Lazy는 ArrangementService를 프록시로 주입하여 순환을 끊습니다.
     */
    public SseRedisSubscriber(@Lazy ArrangementService arrangementService,
                              ObjectMapper objectMapper) {
        this.arrangementService = arrangementService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ProgressEvent event = objectMapper.readValue(
                    message.getBody(), ProgressEvent.class);

            // 이 Pod에 해당 emitter가 있으면 SSE 전송, 없으면 자연스럽게 무시
            arrangementService.deliverSseEventLocally(event);
        } catch (Exception e) {
            log.error("Redis SSE 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
