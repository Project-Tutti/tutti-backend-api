package com.tutti.server.domain.instrument.dto;

import com.tutti.server.domain.instrument.entity.Instrument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InstrumentResponse {

    private Integer midiProgram;
    private String name;
    private String category;
    private boolean generatable;

    public static InstrumentResponse from(Instrument instrument) {
        return InstrumentResponse.builder()
                .midiProgram(instrument.getMidiProgram())
                .name(instrument.getName())
                .category(instrument.getCategory())
                .generatable(instrument.isGeneratable())
                .build();
    }
}
