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
 * 외부 서비스 통신용 WebClient Bean 설정.
 *
 * <h3>등록되는 Bean 3개</h3>
 * <ol>
 * <li>{@code aiWebClient} — AI 편곡 서버 (외부 GPU 서버, Cloudflare Tunnel 경유)</li>
 * <li>{@code supabaseWebClient} — Supabase Storage REST API (파일 저장/조회)</li>
 * <li>{@code converterWebClient} — MIDI↔MusicXML 변환 서비스 (같은 K8s 클러스터)</li>
 * </ol>
 */
@Configuration
public class WebClientConfig {

        // ── AI Server (KEDA 0-Scaling) ──

        @Value("${ai.server.base-url}")
        private String aiServerBaseUrl;

        /** KEDA HTTP Addon이 실제 AI 서버를 식별하기 위한 Host 헤더 값. */
        @Value("${ai.server.host-header:ai-server.tutti.svc.cluster.local}")
        private String aiServerHostHeader;

        @Value("${ai.server.timeout:30000}")
        private int aiTimeout;

        // ── Supabase Storage ──

        @Value("${supabase.storage.url}")
        private String supabaseUrl;

        @Value("${supabase.storage.key}")
        private String supabaseKey;

        // ── Converter Service ──

        @Value("${converter.server.base-url:http://converter-service:8000}")
        private String converterBaseUrl;

        @Value("${converter.server.timeout:60000}")
        private int converterTimeout;

        /**
         * AI 편곡 서버 통신용 WebClient.
         *
         * <p>
         * <b>KEDA 0-Scaling 지원:</b>
         * </p>
         * <ul>
         * <li>baseUrl: KEDA HTTP Addon 인터셉터 프록시 주소 (요청을 대기시킴)</li>
         * <li>Host 헤더: 실제 AI 서버의 K8s 내부 DNS → KEDA가 이 값으로 라우팅</li>
         * </ul>
         * 대용량 파일 전송을 위해 버퍼 크기 16MB로 확장.
         */
        @Bean
        public WebClient aiWebClient() {
                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(16 * 1024 * 1024))
                                .build();

                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, aiTimeout)
                                .responseTimeout(Duration.ofMillis(aiTimeout));

                return WebClient.builder()
                                .baseUrl(aiServerBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .defaultHeader(HttpHeaders.HOST, aiServerHostHeader) // KEDA 라우팅용
                                .exchangeStrategies(strategies)
                                .build();
        }

        /**
         * Supabase Storage REST API 통신용 WebClient.
         * apikey 헤더와 Authorization Bearer 헤더를 자동 설정.
         * 파일 업/다운로드를 위해 버퍼 크기 16MB.
         */
        @Bean
        public WebClient supabaseWebClient() {
                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(16 * 1024 * 1024))
                                .build();

                return WebClient.builder()
                                .baseUrl(supabaseUrl)
                                .defaultHeader("apikey", supabaseKey)
                                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                                .exchangeStrategies(strategies)
                                .build();
        }

        /**
         * MIDI↔MusicXML 변환 서비스 통신용 WebClient.
         * 같은 K8s 클러스터 내부 DNS로 통신 (http://converter-service:8000).
         * 변환 작업은 시간이 걸릴 수 있으므로 타임아웃 60초.
         */
        @Bean
        public WebClient converterWebClient() {
                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(16 * 1024 * 1024))
                                .build();

                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, converterTimeout)
                                .responseTimeout(Duration.ofMillis(converterTimeout));

                return WebClient.builder()
                                .baseUrl(converterBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .exchangeStrategies(strategies)
                                .build();
        }
}
