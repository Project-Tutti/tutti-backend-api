package com.tutti.server.infra.ai.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버 콜백 페이로드.
 *
 * <p>
 * 진행률/실패 콜백: JSON {@code application/json}으로 전송
 * </p>
 * <p>
 * 완료 콜백: {@code multipart/form-data}의 "metadata" 파트에 JSON으로 전송,
 * MIDI 파일은 "file" 파트로 함께 전송
 * </p>
 */
@Getter
@NoArgsConstructor
public class AiCallbackPayload {

    private Long projectId;
    private Long versionId;
    private String status; // "processing", "complete", "failed"
    private Integer progress; // 0-100

    private String errorMessage;
}
