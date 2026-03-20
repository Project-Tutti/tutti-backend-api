package com.tutti.server.domain.instrument.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutti.server.domain.instrument.entity.Instrument;
import com.tutti.server.domain.instrument.repository.InstrumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class InstrumentControllerTest {

    @Test
    @DisplayName("실제 JSON 응답 확인용 테스트")
    void printJsonResponse() throws Exception {
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        InstrumentController controller = new InstrumentController(instrumentRepository);

        Instrument piano = mock(Instrument.class);
        given(piano.getMidiProgram()).willReturn(0);
        given(piano.getName()).willReturn("Acoustic Grand Piano");
        given(piano.getCategory()).willReturn("Piano");
        given(piano.isActive()).willReturn(true);
        given(piano.isGeneratable()).willReturn(true);

        given(instrumentRepository.findByActiveTrueOrderByMidiProgram()).willReturn(List.of(piano));

        ResponseEntity<?> response = controller.getAllInstruments();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody());

        System.out.println("====== ACTUAL JSON RESPONSE ======");
        System.out.println(json);
        System.out.println("==================================");
    }
}
