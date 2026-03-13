package com.tutti.server.infra.ai.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiCallbackPayload {

    private Long projectId;
    private Long versionId;
    private String status; // "complete" or "failed"
    private Integer progress; // 0-100

    // DB v3.0: 결과 파일 경로 3컬럼 분리
    private String resultMidiPath;
    private String resultXmlPath;
    private String resultPdfPath;

    private String errorMessage;
}
