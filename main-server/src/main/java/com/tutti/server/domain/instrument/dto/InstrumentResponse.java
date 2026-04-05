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
    private Integer categoryId;
    private Integer minNote;
    private Integer maxNote;

    public static InstrumentResponse from(Instrument instrument) {
        return InstrumentResponse.builder()
                .midiProgram(instrument.getMidiProgram())
                .name(instrument.getName())
                .categoryId(instrument.getCategory().getRepresentativeProgram())
                .minNote(instrument.getMinNote())
                .maxNote(instrument.getMaxNote())
                .build();
    }
}
