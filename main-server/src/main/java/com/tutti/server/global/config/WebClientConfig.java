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
 * <li>{@code aiWebClient} — AI 편곡 서버 (온프레미스 RTX 4090, Cloudflare Tunnel 경유)</li>
 * <li>{@code supabaseWebClient} — Supabase Storage REST API (파일 저장/조회)</li>
 * <li>{@code converterWebClient} — MIDI↔MusicXML 변환 서비스 (같은 K8s 클러스터)</li>
 * </ol>
 */
@Configuration
public class WebClientConfig {

        // ── AI Server (On-Premise, Cloudflare Tunnel 경유) ──

        @Value("${ai.server.base-url}")
        private String aiServerBaseUrl;

        /** Cloudflare Tunnel이 올바른 서버로 라우팅하기 위한 Host 헤더 값. */
        @Value("${ai.server.host-header:}")
        private String aiServerHostHeader;

        /** AI 서버 인증용 API 키 — 허가된 클라이언트만 요청 가능. */
        @Value("${ai.server.api-key:}")
        private String aiServerApiKey;

        /** Cloudflare Access Service Token — 네트워크 레벨 접근 제어. */
        @Value("${ai.server.cf-access-client-id:}")
        private String cfAccessClientId;

        @Value("${ai.server.cf-access-client-secret:}")
        private String cfAccessClientSecret;

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
         * <b>온프레미스 AI 서버 (Cloudflare Tunnel 경유):</b>
         * </p>
         * <ul>
         * <li>baseUrl: Cloudflare Tunnel 도메인 (AI_SERVER_URL 환경변수로 주입)</li>
         * <li>Host 헤더: Cloudflare Tunnel이 올바른 서버로 라우팅하기 위해 도메인과 일치 필요</li>
         * <li>X-API-Key: AI 서버 인증 — 허가된 클라이언트만 요청 가능</li>
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

                WebClient.Builder builder = WebClient.builder()
                                .baseUrl(aiServerBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .defaultHeader(HttpHeaders.HOST, aiServerHostHeader) // Cloudflare Tunnel 라우팅용
                                .exchangeStrategies(strategies);

                // SEC: AI 서버 API 키가 설정된 경우에만 인증 헤더 추가
                if (aiServerApiKey != null && !aiServerApiKey.isBlank()) {
                        builder.defaultHeader("X-API-Key", aiServerApiKey);
                }

                // SEC: Cloudflare Access Service Token — 네트워크 레벨 접근 제어
                // Cloudflare Access 정책이 설정된 도메인에 접근 시 필수
                if (cfAccessClientId != null && !cfAccessClientId.isBlank()) {
                        builder.defaultHeader("CF-Access-Client-Id", cfAccessClientId);
                        builder.defaultHeader("CF-Access-Client-Secret", cfAccessClientSecret);
                }

                return builder.build();
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
