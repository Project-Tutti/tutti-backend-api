package com.tutti.server.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 서버 통신용 WebClient Bean 설정.
 * ERR-3 FIX: 응답 타임아웃과 연결 타임아웃을 실제 적용합니다.
 */
@Configuration
public class WebClientConfig {

        @Value("${ai.server.base-url}")
        private String aiServerBaseUrl;

        @Value("${ai.server.timeout:30000}")
        private int timeout;

        @Bean
        public WebClient aiWebClient() {
                // 대용량 파일 전송을 위해 버퍼 크기 확장 (16MB)
                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(16 * 1024 * 1024))
                                .build();

                // ERR-3 FIX: 실제 타임아웃 적용
                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                                .responseTimeout(Duration.ofMillis(timeout));

                return WebClient.builder()
                                .baseUrl(aiServerBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .exchangeStrategies(strategies)
                                .build();
        }
}
