package com.tutti.server.infra.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Supabase Storage REST API 클라이언트.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * ProjectService → SupabaseStorageService →(HTTP)→ Supabase Storage API
 * </pre>
 *
 * <h3>사용하는 Bucket</h3>
 * <ul>
 * <li>{@code midi-files} — 원본 MIDI 파일</li>
 * <li>{@code arrangement-results} — AI 편곡 결과물 (MIDI, XML, PDF)</li>
 * </ul>
 */
@Slf4j
@Service
public class SupabaseStorageService {

    public static final String BUCKET_MIDI = "midi-files";
    public static final String BUCKET_RESULTS = "arrangement-results";

    private final WebClient supabaseWebClient;
    private final String supabaseUrl;

    public SupabaseStorageService(
            WebClient supabaseWebClient,
            @Value("${supabase.storage.url}") String supabaseUrl) {
        this.supabaseWebClient = supabaseWebClient;
        this.supabaseUrl = supabaseUrl;
    }

    /**
     * 파일을 Supabase Storage에 업로드합니다.
     * x-upsert: true 헤더로 동일 경로에 덮어쓰기를 허용합니다.
     */
    public void upload(String bucket, String path, byte[] data, String contentType) {
        supabaseWebClient.post()
                .uri("/storage/v1/object/{bucket}/{path}", bucket, path)
                .header("x-upsert", "true")
                .contentType(MediaType.parseMediaType(contentType))
                .bodyValue(data)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(res -> log.info("Supabase Storage 업로드 완료: {}/{}", bucket, path))
                .doOnError(err -> log.error("Supabase Storage 업로드 실패: {}/{}, error={}",
                        bucket, path, err.getMessage()))
                .block();
    }

    /**
     * Supabase Storage에서 파일을 다운로드합니다.
     */
    public byte[] download(String bucket, String path) {
        return supabaseWebClient.get()
                .uri("/storage/v1/object/{bucket}/{path}", bucket, path)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnError(err -> log.error("Supabase Storage 다운로드 실패: {}/{}, error={}",
                        bucket, path, err.getMessage()))
                .block();
    }

    /**
     * 파일을 다운로드하여 Spring Resource로 반환합니다.
     */
    public Resource downloadAsResource(String bucket, String path) {
        byte[] data = download(bucket, path);
        if (data == null) {
            return null;
        }
        return new ByteArrayResource(data);
    }

    /**
     * Supabase Storage의 Public URL을 생성합니다.
     * AI 서버에 파일 위치를 전달할 때 사용합니다.
     */
    public String getPublicUrl(String bucket, String path) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }
}
