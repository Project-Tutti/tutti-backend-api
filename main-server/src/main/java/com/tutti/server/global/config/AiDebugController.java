package com.tutti.server.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AiDebugController {

    private final WebClient aiWebClient;

    @GetMapping("/api/debug/ai-ping")
    public Mono<String> pingAiServer() {
        return aiWebClient.get()
                .uri("/api/v1/health") // 가정: AI 서버에 /api/v1/health 또는 루트가 있음 (여기서는 /health로 해봄)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("ERROR OCCURRED: " + e.getClass().getName() + " - " + e.getMessage()));
    }

    @GetMapping("/api/debug/ai-arrange-ping")
    public Mono<String> pingAiArrange() {
        return aiWebClient.post()
                .uri("/api/v1/arrange") // POST로 빈 바디
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("ERROR OCCURRED: " + e.getClass().getName() + " - " + e.getMessage()));
    }
}
