package com.tutti.server.infra.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 서버 편곡 요청 DTO.
 *
 * <p>
 * 카테고리 기반 생성 모델 전환에 따라 다음 필드가 추가됨:
 * <ul>
 *   <li>{@code targetInstrumentId} — 생성 대상 카테고리의 representative_program</li>
 *   <li>{@code minNote}, {@code maxNote} — 생성 음역대 제한</li>
 *   <li>{@code modelType} — AI 모델 선택 힌트 (예: "solo_string", "brass")</li>
 *   <li>{@code genre} — 생성 장르 (GENRE_{genre} 토큰으로 변환)</li>
 *   <li>{@code temperature} — 생성 다양성 제어 (기본 1.0)</li>
 * </ul>
 * </p>
 */
@Getter
@Builder
@AllArgsConstructor
public class AiArrangeRequest {

    private Long projectId;
    private Long versionId;
    private String midiFilePath;
    private List<MappingData> mappings;

    /** 생성 대상 카테고리의 representative_program (AI 모델 추론용). */
    private Integer targetInstrumentId;

    /** 실제 악기 이름 (예: "Viola"). MIDI 결과 트랙 명칭에 사용. */
    private String targetInstrumentName;

    /** 실제 MIDI program 번호 (예: 41=Viola). MIDI program_change에 사용. */
    private Integer targetMidiProgram;

    /** 생성 음역대 하한 (MIDI 0~127). null이면 AI 서버에서 기본값 사용. */
    private Integer minNote;

    /** 생성 음역대 상한 (MIDI 0~127). null이면 AI 서버에서 기본값 사용. */
    private Integer maxNote;

    /** AI 모델 선택 힌트 — 카테고리명 기반 (예: "solo_string"). */
    private String modelType;

    /** 생성 장르 — AI 서버에서 GENRE_{genre} 토큰으로 변환. 기본값 "CLASSICAL". */
    private String genre;

    /** Temperature — 생성 다양성 제어 (기본 1.0). */
    private Double temperature;

    private String callbackUrl;
    private String callbackSecret;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MappingData {
        private Integer trackIndex;
        private Integer targetInstrumentId;
    }
}

