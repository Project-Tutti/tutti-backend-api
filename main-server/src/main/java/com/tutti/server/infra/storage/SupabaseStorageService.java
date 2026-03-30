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

    /**
     * Supabase Storage의 Signed URL을 생성합니다.
     * Private 버킷의 파일에 대해 시간 제한이 있는 임시 다운로드 URL을 발급합니다.
     *
     * <p>
     * service_role key를 사용하므로 RLS를 우회하며,
     * 생성된 URL은 지정된 만료 시간이 지나면 자동으로 무효화됩니다.
     * </p>
     *
     * @param bucket    Supabase Storage 버킷 이름
     * @param path      버킷 내 파일 경로
     * @param expiresIn URL 유효 시간 (초 단위)
     * @return 완전한 Signed URL (Supabase 도메인 포함)
     */
    public String createSignedUrl(String bucket, String path, int expiresIn) {
        String uri = "/storage/v1/object/sign/" + bucket + "/" + path;
        String response = supabaseWebClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"expiresIn\":" + expiresIn + "}")
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(err -> log.error("Supabase Signed URL 생성 실패: {}/{}, error={}",
                        bucket, path, err.getMessage()))
                .block();

        // 응답: { "signedURL": "/storage/v1/object/sign/bucket/path?token=..." }
        // signedURL은 상대 경로이므로 Supabase URL을 앞에 붙여야 함
        if (response != null && response.contains("signedURL")) {
            String signedPath = response.split("\"signedURL\"\\s*:\\s*\"")[1].split("\"")[0];
            // Supabase API가 /storage/v1을 생략해서 반환하므로 절대 경로에 보정
            if (!signedPath.startsWith("/storage/v1")) {
                signedPath = "/storage/v1" + signedPath;
            }
            return supabaseUrl + signedPath;
        }

        throw new RuntimeException("Supabase Signed URL 생성에 실패했습니다.");
    }
}
