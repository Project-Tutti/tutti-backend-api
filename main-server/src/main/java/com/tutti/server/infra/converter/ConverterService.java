package com.tutti.server.infra.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * MIDI ↔ MusicXML 변환 서비스 클라이언트.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * ProjectService → ConverterService →(HTTP)→ converter-service (같은 K8s 클러스터)
 * </pre>
 *
 * <p>
 * Converter 서비스가 아직 배포되지 않은 경우에도 main-server는 정상 동작합니다.
 * 변환 요청이 실패하면 로그를 남기고 null을 반환합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConverterService {

    private final WebClient converterWebClient;

    /**
     * MIDI 파일을 MusicXML로 변환합니다.
     *
     * @param midiBytes MIDI 파일 바이트 배열
     * @return MusicXML 바이트 배열, 실패 시 null
     */
    public byte[] midiToMusicXml(byte[] midiBytes) {
        try {
            return converterWebClient.post()
                    .uri("/api/v1/convert/midi-to-xml")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(midiBytes)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("MIDI→MusicXML 변환 실패: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Converter 서비스 연결 불가 (아직 미배포?): {}", e.getMessage());
            return null;
        }
    }

    /**
     * MusicXML 파일을 MIDI로 변환합니다.
     *
     * @param xmlBytes MusicXML 파일 바이트 배열
     * @return MIDI 바이트 배열, 실패 시 null
     */
    public byte[] musicXmlToMidi(byte[] xmlBytes) {
        try {
            return converterWebClient.post()
                    .uri("/api/v1/convert/xml-to-midi")
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(xmlBytes)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("MusicXML→MIDI 변환 실패: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Converter 서비스 연결 불가 (아직 미배포?): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converter 서비스의 헬스체크.
     * 
     * @return 서비스 가용 여부
     */
    public boolean isAvailable() {
        try {
            converterWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
