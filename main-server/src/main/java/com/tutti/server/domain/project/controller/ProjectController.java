package com.tutti.server.domain.project.controller;

import com.tutti.server.domain.project.dto.request.ProjectCreateRequest;
import com.tutti.server.domain.project.dto.request.ProjectRenameRequest;
import com.tutti.server.domain.project.dto.request.RegenerateRequest;
import com.tutti.server.domain.project.dto.request.VersionRenameRequest;
import com.tutti.server.domain.project.dto.response.*;
import com.tutti.server.domain.project.service.ArrangementService;
import com.tutti.server.domain.project.service.ProjectService;
import com.tutti.server.global.auth.AuthUtils;
import com.tutti.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 프로젝트 컨트롤러 — MIDI 편곡 프로젝트의 전체 API 엔드포인트를 제공합니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client HTTP] → <b>ProjectController</b> (인증 + 파라미터 검증)
 *               → ProjectService (비즈니스 로직)
 *               → ArrangementService (AI 서버 비동기 통신)
 *               → Repository (DB)
 * </pre>
 *
 * <h3>엔드포인트 목록 (11개)</h3>
 * <table>
 * <tr>
 * <td>POST</td>
 * <td>/api/projects</td>
 * <td>프로젝트 생성 (MIDI 업로드)</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/api/projects/{id}</td>
 * <td>상세 조회</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/api/projects/{id}/tracks</td>
 * <td>트랙 정보</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/api/projects/{pid}/{vid}/status</td>
 * <td>진행률 SSE</td>
 * </tr>
 * <tr>
 * <td>POST</td>
 * <td>/api/projects/{id}/regenerate</td>
 * <td>재생성</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/api/projects/{pid}/{vid}/score</td>
 * <td>악보 조회</td>
 * </tr>
 * <tr>
 * <td>GET</td>
 * <td>/api/projects/{pid}/{vid}/download</td>
 * <td>파일 다운로드</td>
 * </tr>
 * <tr>
 * <td>PATCH</td>
 * <td>/api/projects/{id}</td>
 * <td>이름 수정</td>
 * </tr>
 * <tr>
 * <td>DELETE</td>
 * <td>/api/projects/{id}</td>
 * <td>삭제</td>
 * </tr>
 * <tr>
 * <td>PATCH</td>
 * <td>/api/projects/{pid}/{vid}</td>
 * <td>버전 이름 수정</td>
 * </tr>
 * <tr>
 * <td>DELETE</td>
 * <td>/api/projects/{pid}/{vid}</td>
 * <td>버전 삭제</td>
 * </tr>
 * </table>
 *
 * <h3>공통 패턴</h3>
 * <ol>
 * <li>모든 엔드포인트에서 {@code AuthUtils.extractUserId(authentication)}로 JWT → UUID
 * 추출</li>
 * <li>Service에서 소유권 검증 수행 (자신의 프로젝트만 접근 가능)</li>
 * <li>응답은 {@code ApiResponse.success()} 래퍼로 통일</li>
 * </ol>
 *
 * @see ProjectService
 * @see ArrangementService
 */
@Tag(name = "Project", description = "프로젝트 편곡 API")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

        private final ProjectService projectService;
        private final ArrangementService arrangementService;

        // ── 3.1 프로젝트 생성 ──

        /**
         * MIDI 파일 업로드 + 프로젝트 생성 + AI 편곡 시작.
         *
         * <p>
         * 이 엔드포인트는 {@code multipart/form-data}를 사용합니다.
         * {@code @RequestPart}로 파일과 JSON 데이터를 분리하여 받습니다.
         * </p>
         *
         * <pre>
         * curl -X POST /api/projects \
         *   -F "file=@song.mid" \
         *   -F "request={\"name\": \"My Project\", ...};type=application/json"
         * </pre>
         *
         * @return 201 Created — 프로젝트 ID, 버전 ID, 현재 상태
         */
        @Operation(summary = "프로젝트 생성", description = "MIDI 파일을 업로드하여 새 프로젝트를 생성합니다.")
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<ProjectCreateResponse>> createProject(
                        Authentication authentication,
                        @RequestPart("file") MultipartFile file,
                        @Parameter(description = "프로젝트 생성 데이터 (JSON)", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProjectCreateRequest.class))) @Valid @RequestPart("request") ProjectCreateRequest request) {
                UUID userId = AuthUtils.extractUserId(authentication);
                ProjectCreateResponse result = projectService.createProject(userId, file, request);
                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(ApiResponse.success("프로젝트가 생성되었습니다. 첫 번째 편곡을 시작합니다.", result));
        }

        // ── 3.2 프로젝트 조회 ──

        /**
         * 프로젝트 상세 조회 — 버전 히스토리와 매핑 정보 포함.
         *
         * @PathVariable: URL 경로의 {projectId} 부분을 메서드 파라미터로 매핑합니다.
         *                예: GET /api/projects/42 → projectId = 42
         */
        @Operation(summary = "프로젝트 조회", description = "특정 프로젝트의 상세 정보와 버전 히스토리를 조회합니다.")
        @GetMapping("/{projectId}")
        public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProject(
                        Authentication authentication,
                        @PathVariable Long projectId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                ProjectDetailResponse result = projectService.getProject(userId, projectId);
                return ResponseEntity.ok(ApiResponse.success("프로젝트를 조회하였습니다.", result));
        }

        // ── 3.3 트랙 정보 조회 ──

        /** 원본 MIDI 파일의 트랙 목록 — "재생성" 화면에서 악기 매핑 UI에 사용. */
        @Operation(summary = "트랙 정보 조회", description = "프로젝트의 MIDI 트랙 정보를 조회합니다.")
        @GetMapping("/{projectId}/tracks")
        public ResponseEntity<ApiResponse<TrackInfoResponse>> getTracks(
                        Authentication authentication,
                        @PathVariable Long projectId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                TrackInfoResponse result = projectService.getTracks(userId, projectId);
                return ResponseEntity.ok(ApiResponse.success("트랙 정보를 조회하였습니다.", result));
        }

        // ── 3.4 진행률 조회 (SSE) ──

        /**
         * 편곡 진행률 실시간 스트리밍 (SSE).
         *
         * <p>
         * {@code produces = TEXT_EVENT_STREAM_VALUE}: HTTP Content-Type을
         * {@code text/event-stream}으로 설정하여 SSE 프로토콜을 활성화합니다.
         * 브라우저의 EventSource API로 연결할 수 있습니다.
         * </p>
         *
         * <pre>
         * // 프론트엔드 사용 예시
         * const sse = new EventSource("/api/projects/1/1/status");
         * sse.addEventListener("progress", (e) => {
         *   const data = JSON.parse(e.data);
         *   console.log(data.result.progress + "%");
         * });
         * </pre>
         *
         * @return SseEmitter — 연결을 유지하며 이벤트를 지속 전송
         */
        @Operation(summary = "진행률 조회 (SSE)", description = "편곡 처리 진행률을 실시간으로 조회합니다.")
        @GetMapping(value = "/{projectId}/{versionId}/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter getStatus(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @PathVariable Long versionId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                // 소유권 검증만 수행하고, SSE 구독은 ArrangementService에 위임
                projectService.verifyOwnership(userId, projectId);
                return arrangementService.subscribe(projectId, versionId);
        }

        // ── 3.5 재생성 (새 버전 생성) ──

        /** 동일 MIDI에 다른 악기 매핑을 적용하여 새로운 편곡 버전 생성. */
        @Operation(summary = "재생성", description = "기존 프로젝트에서 새로운 버전의 편곡을 생성합니다.")
        @PostMapping("/{projectId}/regenerate")
        public ResponseEntity<ApiResponse<ProjectCreateResponse>> regenerate(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @Valid @RequestBody RegenerateRequest request) {
                UUID userId = AuthUtils.extractUserId(authentication);
                ProjectCreateResponse result = projectService.regenerate(userId, projectId, request);
                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(ApiResponse.success("새로운 버전 생성을 시작합니다.", result));
        }

        // ── 3.6 악보 데이터 조회 ──

        /**
         * MusicXML 악보 데이터 조회 — 프론트엔드 악보 뷰어 렌더링용.
         * Content-Type: application/xml, Content-Disposition: inline으로 설정하여
         * 브라우저에서 바로 렌더링할 수 있습니다.
         */
        @Operation(summary = "악보 데이터 조회", description = "특정 버전의 악보(MusicXML) 데이터를 조회합니다.")
        @GetMapping("/{projectId}/{versionId}/score")
        public ResponseEntity<Resource> getScore(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @PathVariable Long versionId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                Resource resource = projectService.getScore(userId, projectId, versionId);

                return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "inline; filename=\"result.xml\"")
                                .header("X-Project-Id", projectId.toString())
                                .header("X-Version-Id", versionId.toString())
                                .body(resource);
        }

        // ── 3.7 파일 다운로드 ──

        /**
         * 편곡 결과 파일 다운로드.
         *
         * <p>
         * Content-Disposition: {@code attachment}로 설정하여
         * 브라우저가 파일 저장 다이얼로그를 표시합니다.
         * </p>
         *
         * @param type "midi", "xml", "pdf" 중 하나 (쿼리 파라미터)
         */
        @Operation(summary = "파일 다운로드", description = "완성된 편곡 결과물을 다양한 형식으로 다운로드합니다.")
        @GetMapping("/{projectId}/{versionId}/download")
        public ResponseEntity<Resource> downloadFile(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @PathVariable Long versionId,
                        @RequestParam String type) {
                UUID userId = AuthUtils.extractUserId(authentication);
                Resource resource = projectService.downloadFile(userId, projectId, versionId, type);

                DownloadFileType fileType = DownloadFileType.fromString(type);
                String filename = "result" + (fileType != null ? fileType.getExtension() : ".mid");

                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(
                                                fileType != null ? fileType.getContentType()
                                                                : "application/octet-stream"))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + filename + "\"")
                                .body(resource);
        }

        // ── 3.8 프로젝트 이름 수정 ──

        /**
         * 프로젝트 이름 변경.
         *
         * @PatchMapping: HTTP PATCH 메서드에 매핑. 부분 수정(Partial Update)을 의미합니다.
         *                PUT은 전체 교체, PATCH는 일부 필드만 변경할 때 사용합니다.
         */
        @Operation(summary = "프로젝트 이름 수정", description = "프로젝트의 이름을 수정합니다.")
        @PatchMapping("/{projectId}")
        public ResponseEntity<ApiResponse<ProjectRenameResponse>> renameProject(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @Valid @RequestBody ProjectRenameRequest request) {
                UUID userId = AuthUtils.extractUserId(authentication);
                ProjectRenameResponse result = projectService.renameProject(userId, projectId, request);
                return ResponseEntity.ok(ApiResponse.success("프로젝트 이름을 수정하였습니다.", result));
        }

        // ── 3.9 프로젝트 삭제 ──

        /** 프로젝트 삭제 (Soft Delete) — 실제 DB 레코드는 남아있고 deletedAt이 설정됩니다. */
        @Operation(summary = "프로젝트 삭제", description = "프로젝트와 관련된 모든 버전 및 파일을 삭제합니다.")
        @DeleteMapping("/{projectId}")
        public ResponseEntity<ApiResponse<Void>> deleteProject(
                        Authentication authentication,
                        @PathVariable Long projectId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                projectService.deleteProject(userId, projectId);
                return ResponseEntity.ok(ApiResponse.success("프로젝트를 삭제하였습니다."));
        }

        // ── 3.10 버전 이름 수정 ──

        /** 특정 편곡 버전의 이름 변경. */
        @Operation(summary = "버전 이름 수정", description = "특정 버전의 이름을 수정합니다.")
        @PatchMapping("/{projectId}/{versionId}")
        public ResponseEntity<ApiResponse<VersionRenameResponse>> renameVersion(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @PathVariable Long versionId,
                        @Valid @RequestBody VersionRenameRequest request) {
                UUID userId = AuthUtils.extractUserId(authentication);
                VersionRenameResponse result = projectService.renameVersion(userId, projectId, versionId, request);
                return ResponseEntity.ok(ApiResponse.success("버전 이름을 수정하였습니다.", result));
        }

        // ── 3.11 버전 삭제 ──

        /** 버전 삭제 (Soft Delete) — 마지막 버전은 삭제 불가. */
        @Operation(summary = "버전 삭제", description = "특정 버전을 삭제합니다.")
        @DeleteMapping("/{projectId}/{versionId}")
        public ResponseEntity<ApiResponse<Void>> deleteVersion(
                        Authentication authentication,
                        @PathVariable Long projectId,
                        @PathVariable Long versionId) {
                UUID userId = AuthUtils.extractUserId(authentication);
                projectService.deleteVersion(userId, projectId, versionId);
                return ResponseEntity.ok(ApiResponse.success("버전을 삭제하였습니다."));
        }
}
