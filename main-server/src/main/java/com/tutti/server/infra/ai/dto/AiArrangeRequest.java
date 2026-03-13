package com.tutti.server.infra.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AiArrangeRequest {

    private Long projectId;
    private Long versionId;
    private String midiFilePath;
    private List<MappingData> mappings;
    private String callbackUrl;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MappingData {
        private Integer trackIndex;
        private Integer targetInstrumentId;
    }
}
