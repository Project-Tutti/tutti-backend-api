package com.tutti.server.domain.instrument.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InstrumentListResponse {

    private List<InstrumentResponse> instruments;
}
