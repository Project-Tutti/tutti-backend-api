package com.tutti.server.domain.instrument.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutti.server.domain.instrument.dto.InstrumentCategoryResponse;
import com.tutti.server.domain.instrument.dto.InstrumentResponse;
import com.tutti.server.domain.instrument.service.InstrumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class InstrumentControllerTest {

    @Test
    @DisplayName("전체 악기 목록 JSON 응답 확인")
    void printJsonResponse() throws Exception {
        InstrumentService instrumentService = mock(InstrumentService.class);
        InstrumentController controller = new InstrumentController(instrumentService);

        InstrumentResponse piano = InstrumentResponse.builder()
                .midiProgram(0)
                .name("Acoustic Grand Piano")
                .categoryId(0)
                .minNote(21)
                .maxNote(108)
                .build();

        given(instrumentService.getAllActiveInstruments()).willReturn(List.of(piano));

        ResponseEntity<?> response = controller.getAllInstruments();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody());

        System.out.println("====== ACTUAL JSON RESPONSE ======");
        System.out.println(json);
        System.out.println("==================================");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("전체 카테고리 목록 조회")
    void getAllCategories() throws Exception {
        InstrumentService instrumentService = mock(InstrumentService.class);
        InstrumentController controller = new InstrumentController(instrumentService);

        InstrumentCategoryResponse category = InstrumentCategoryResponse.builder()
                .representativeProgram(40)
                .name("Solo String")
                .generatable(true)
                .build();

        given(instrumentService.getAllCategories()).willReturn(List.of(category));

        ResponseEntity<?> response = controller.getAllCategories();

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody());
        System.out.println("====== CATEGORIES RESPONSE ======");
        System.out.println(json);
        System.out.println("=================================");
    }

    @Test
    @DisplayName("생성 가능 카테고리 목록 조회")
    void getGeneratableCategories() throws Exception {
        InstrumentService instrumentService = mock(InstrumentService.class);
        InstrumentController controller = new InstrumentController(instrumentService);

        InstrumentResponse violin = InstrumentResponse.builder()
                .midiProgram(40)
                .name("Violin")
                .categoryId(40)
                .minNote(55)
                .maxNote(103)
                .build();

        InstrumentCategoryResponse category = InstrumentCategoryResponse.builder()
                .representativeProgram(40)
                .name("Solo String")
                .generatable(true)
                .instruments(List.of(violin))
                .build();

        given(instrumentService.getGeneratableCategories()).willReturn(List.of(category));

        ResponseEntity<?> response = controller.getGeneratableCategories();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
