package com.tutti.server.infra.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * MIDI ↔ MusicXML ↔ PDF 변환 서비스 클라이언트.
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
        return convert("/api/v1/convert/midi-to-xml",
                MediaType.APPLICATION_OCTET_STREAM, midiBytes, "MIDI→MusicXML");
    }

    /**
     * MIDI 파일을 MusicXML로 변환합니다 (악보 제목 포함).
     * <p>
     * Converter 서비스에 X-Score-Title 헤더를 전달하여,
     * 변환된 MusicXML에 프로젝트 이름이 악보 제목으로 주입됩니다.
     * </p>
     *
     * @param midiBytes MIDI 파일 바이트 배열
     * @param title     악보 제목 (프로젝트 이름)
     * @return MusicXML 바이트 배열, 실패 시 null
     */
    public byte[] midiToMusicXml(byte[] midiBytes, String title) {
        return convertWithTitle("/api/v1/convert/midi-to-xml",
                MediaType.APPLICATION_OCTET_STREAM, midiBytes, "MIDI→MusicXML", title);
    }

    /**
     * MusicXML 파일을 MIDI로 변환합니다.
     *
     * @param xmlBytes MusicXML 파일 바이트 배열
     * @return MIDI 바이트 배열, 실패 시 null
     */
    public byte[] musicXmlToMidi(byte[] xmlBytes) {
        return convert("/api/v1/convert/xml-to-midi",
                MediaType.APPLICATION_XML, xmlBytes, "MusicXML→MIDI");
    }

    /**
     * MIDI 파일을 PDF 악보로 변환합니다.
     *
     * @param midiBytes MIDI 파일 바이트 배열
     * @return PDF 바이트 배열, 실패 시 null
     */
    public byte[] midiToPdf(byte[] midiBytes) {
        return convert("/api/v1/convert/midi-to-pdf",
                MediaType.APPLICATION_OCTET_STREAM, midiBytes, "MIDI→PDF");
    }

    /**
     * MusicXML 파일을 PDF 악보로 변환합니다.
     *
     * @param xmlBytes MusicXML 파일 바이트 배열
     * @return PDF 바이트 배열, 실패 시 null
     */
    public byte[] musicXmlToPdf(byte[] xmlBytes) {
        return convert("/api/v1/convert/xml-to-pdf",
                MediaType.APPLICATION_XML, xmlBytes, "MusicXML→PDF");
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

    // ── 내부 공통 메서드 ──

    private byte[] convert(String uri, MediaType contentType, byte[] data, String label) {
        try {
            return converterWebClient.post()
                    .uri(uri)
                    .contentType(contentType)
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("{} 변환 실패: status={}, body={}",
                    label, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Converter 서비스 연결 불가 (아직 미배포?): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 공통 변환 메서드 + X-Score-Title 헤더 전달.
     * 악보 제목이 필요한 변환(MIDI→MusicXML)에서 사용합니다.
     */
    private byte[] convertWithTitle(String uri, MediaType contentType,
            byte[] data, String label, String title) {
        try {
            var requestSpec = converterWebClient.post()
                    .uri(uri)
                    .contentType(contentType);

            if (title != null && !title.isBlank()) {
                requestSpec = requestSpec.header("X-Score-Title", title);
            }

            return requestSpec
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("{} 변환 실패: status={}, body={}",
                    label, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Converter 서비스 연결 불가 (아직 미배포?): {}", e.getMessage());
            return null;
        }
    }
}
