package com.tutti.server.domain.project.service;

import com.tutti.server.domain.instrument.repository.InstrumentCategoryRepository;
import com.tutti.server.domain.project.entity.ProjectVersion;
import com.tutti.server.domain.project.repository.ProjectVersionRepository;
import com.tutti.server.domain.project.repository.VersionMappingRepository;
import com.tutti.server.infra.ai.dto.AiCallbackPayload;
import com.tutti.server.infra.converter.ConverterService;
import com.tutti.server.infra.storage.SupabaseStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArrangementService 단위 테스트")
class ArrangementServiceTest {

    @InjectMocks
    private ArrangementService arrangementService;

    @Mock
    private WebClient aiWebClient;
    @Mock
    private ProjectVersionRepository versionRepository;
    @Mock
    private VersionMappingRepository mappingRepository;
    @Mock
    private SupabaseStorageService storageService;
    @Mock
    private ConverterService converterService;
    @Mock
    private InstrumentCategoryRepository categoryRepository;

    // ══════════════════════════════════════
    // handleCallbackWithFile() — Multipart 완료 콜백
    // ══════════════════════════════════════

    @Nested
    @DisplayName("handleCallbackWithFile()")
    class HandleCallbackWithFile {

        @Test
        @DisplayName("COMPLETE — MIDI 업로드 + XML/PDF 변환 + DB 업데이트")
        void 정상_콜백_완료() {
            // given
            AiCallbackPayload payload = createPayload(1L, 1L, "complete", 100);

            byte[] midiBytes = new byte[] { 0x4D, 0x54, 0x68, 0x64 }; // MIDI header

            ProjectVersion version = ProjectVersion.builder()
                    .name("Ver 1")
                    .status(ProjectVersion.VersionStatus.PROCESSING)
                    .build();
            ReflectionTestUtils.setField(version, "id", 1L);
            given(versionRepository.findById(1L)).willReturn(Optional.of(version));

            // Converter mock
            byte[] xmlBytes = "<score>".getBytes();
            byte[] pdfBytes = new byte[] { 0x25, 0x50, 0x44, 0x46 }; // PDF header
            given(converterService.midiToMusicXml(midiBytes)).willReturn(xmlBytes);
            given(converterService.midiToPdf(midiBytes)).willReturn(pdfBytes);

            // when
            arrangementService.handleCallbackWithFile(payload, midiBytes);

            // then
            assertThat(version.getStatus()).isEqualTo(ProjectVersion.VersionStatus.COMPLETE);
            assertThat(version.getProgress()).isEqualTo(100);
            assertThat(version.getResultMidiPath()).isEqualTo("1/1/result.mid");
            assertThat(version.getResultXmlPath()).isEqualTo("1/1/result.musicxml");
            assertThat(version.getResultPdfPath()).isEqualTo("1/1/result.pdf");
            verify(versionRepository).save(version);

            // Supabase 업로드 검증
            verify(storageService).upload(
                    SupabaseStorageService.BUCKET_RESULTS,
                    "1/1/result.mid", midiBytes, "audio/midi");
        }
    }

    // ══════════════════════════════════════
    // handleCallback() — JSON 진행률/실패 콜백
    // ══════════════════════════════════════

    @Nested
    @DisplayName("handleCallback()")
    class HandleCallback {

        @Test
        @DisplayName("FAILED 상태 — 실패로 업데이트")
        void 콜백_실패상태() {
            // given
            AiCallbackPayload payload = createPayload(1L, 1L, "failed", 30);

            ProjectVersion version = ProjectVersion.builder()
                    .name("Ver 1")
                    .status(ProjectVersion.VersionStatus.PROCESSING)
                    .build();
            ReflectionTestUtils.setField(version, "id", 1L);
            given(versionRepository.findById(1L)).willReturn(Optional.of(version));

            // when
            arrangementService.handleCallback(payload);

            // then
            assertThat(version.getStatus()).isEqualTo(ProjectVersion.VersionStatus.FAILED);
        }

        @Test
        @DisplayName("알 수 없는 상태값 — FAILED로 폴백")
        void 알수없는_상태_폴백() {
            // given
            AiCallbackPayload payload = createPayload(1L, 1L, "unknown_status", 0);

            ProjectVersion version = ProjectVersion.builder()
                    .name("Ver 1")
                    .status(ProjectVersion.VersionStatus.PROCESSING)
                    .build();
            ReflectionTestUtils.setField(version, "id", 1L);
            given(versionRepository.findById(1L)).willReturn(Optional.of(version));

            // when
            arrangementService.handleCallback(payload);

            // then
            assertThat(version.getStatus()).isEqualTo(ProjectVersion.VersionStatus.FAILED);
        }
    }

    // ══════════════════════════════════════
    // SSE 구독
    // ══════════════════════════════════════

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("정상 구독 — SseEmitter 반환")
        void 정상_구독() {
            // given
            ReflectionTestUtils.setField(arrangementService, "callbackBaseUrl", "http://test");
            ReflectionTestUtils.setField(arrangementService, "callbackSecret", "secret");

            ProjectVersion version = ProjectVersion.builder()
                    .name("Ver 1")
                    .status(ProjectVersion.VersionStatus.PROCESSING)
                    .progress(50)
                    .build();
            ReflectionTestUtils.setField(version, "id", 1L);
            given(versionRepository.findById(1L)).willReturn(Optional.of(version));

            // when
            SseEmitter emitter = arrangementService.subscribe(1L, 1L);

            // then
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 버전 ID — emitter는 반환하지만 초기 이벤트 없음")
        void 존재하지않는_버전() {
            // given
            ReflectionTestUtils.setField(arrangementService, "callbackBaseUrl", "http://test");
            ReflectionTestUtils.setField(arrangementService, "callbackSecret", "secret");
            given(versionRepository.findById(999L)).willReturn(Optional.empty());

            // when
            SseEmitter emitter = arrangementService.subscribe(1L, 999L);

            // then
            assertThat(emitter).isNotNull();
        }
    }

    // ══════════════════════════════════════
    // AI 요청 실패 처리
    // ══════════════════════════════════════

    @Nested
    @DisplayName("handleAiRequestFailure()")
    class HandleFailure {

        @Test
        @DisplayName("AI 요청 실패 시 — FAILED 상태로 업데이트")
        void AI요청_실패() {
            // given
            ProjectVersion version = ProjectVersion.builder()
                    .name("Ver 1")
                    .status(ProjectVersion.VersionStatus.PROCESSING)
                    .build();
            ReflectionTestUtils.setField(version, "id", 1L);
            given(versionRepository.findById(1L)).willReturn(Optional.of(version));

            // when
            arrangementService.handleAiRequestFailure(1L);

            // then
            assertThat(version.getStatus()).isEqualTo(ProjectVersion.VersionStatus.FAILED);
            assertThat(version.getProgress()).isEqualTo(0);
            verify(versionRepository).save(version);
        }
    }

    // ── Helper ──

    private AiCallbackPayload createPayload(Long projectId, Long versionId,
            String status, Integer progress) {
        AiCallbackPayload payload = new AiCallbackPayload();
        ReflectionTestUtils.setField(payload, "projectId", projectId);
        ReflectionTestUtils.setField(payload, "versionId", versionId);
        ReflectionTestUtils.setField(payload, "status", status);
        ReflectionTestUtils.setField(payload, "progress", progress);
        return payload;
    }
}
