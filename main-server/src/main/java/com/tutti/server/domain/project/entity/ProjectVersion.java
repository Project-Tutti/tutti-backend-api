package com.tutti.server.domain.project.entity;

import com.tutti.server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 편곡 버전 엔티티 — DB의 "project_versions" 테이블에 매핑됩니다.
 *
 * <h3>도메인 개념</h3>
 * 하나의 프로젝트에서 여러 번 편곡을 시도할 수 있으며, 각 시도가 하나의 "버전"입니다.
 * 사용자는 악기 매핑을 변경하여 "재생성"할 때마다 새로운 버전이 만들어집니다.
 *
 * <h3>상태 머신 (VersionStatus)</h3>
 * 
 * <pre>
 * PENDING → PROCESSING → COMPLETE
 *              ↓
 *           FAILED
 * </pre>
 * <ul>
 * <li><b>PENDING</b>: 생성 직후, AI 서버에 요청을 보내기 전</li>
 * <li><b>PROCESSING</b>: AI 서버가 편곡 작업을 처리 중 (SSE로 진행률 전송)</li>
 * <li><b>COMPLETE</b>: 편곡 완료, 결과 파일(MIDI/XML/PDF) 다운로드 가능</li>
 * <li><b>FAILED</b>: AI 서버 오류 또는 타임아웃으로 실패</li>
 * </ul>
 *
 * <h3>결과 파일 구조 (DB v3.0)</h3>
 * 편곡 결과물은 MIDI, MusicXML, PDF 세 가지 형식으로 생성되며,
 * 각각 별도의 컬럼에 파일 경로를 저장합니다.
 *
 * @see Project
 * @see VersionMapping
 * @see com.tutti.server.domain.project.service.ArrangementService
 */
@Entity
@Table(name = "project_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProjectVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 프로젝트 — 다대일(N:1) 관계.
     * LAZY 로딩으로 불필요한 JOIN을 방지합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 버전 표시 이름 (예: "Ver 1", "피아노 솔로 버전"). */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 현재 편곡 처리 상태.
     *
     * @Enumerated(STRING): DB에 "PENDING", "PROCESSING" 등 문자열로 저장.
     * 
     * @Builder.Default: 빌더로 생성 시 기본값은 PENDING(대기 중).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VersionStatus status = VersionStatus.PENDING;

    /** 이 버전에서 사용할 목표 카테고리의 representative_program (예: 40=Solo String). */
    private Integer instrumentId;

    /** 생성 음역대 하한 — 사용자가 설정한 MIDI 노트 번호 (0~127). */
    @Column(name = "min_note")
    private Integer minNote;

    /** 생성 음역대 상한 — 사용자가 설정한 MIDI 노트 번호 (0~127). */
    @Column(name = "max_note")
    private Integer maxNote;

    /** 생성 장르 — AI 서버에서 GENRE_{genre} 토큰으로 변환. 기본값 CLASSICAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "genre", length = 20)
    @Builder.Default
    private Genre genre = Genre.CLASSICAL;

    /** 생성 Temperature — 생성 다양성 제어 (0.1~2.0). 기본값 1.0. */
    @Column(name = "temperature")
    @Builder.Default
    private Double temperature = 1.0;

    /**
     * 편곡 진행률 (0~100%).
     * AI 서버가 SSE로 진행률을 전송하면 이 값이 업데이트됩니다.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer progress = 0;

    // ── 결과 파일 경로 (DB v3.0: 단일 컬럼 → 3컬럼 분리) ──
    // 왜 분리했나? → 기존에는 resultFilePath 하나에 저장하고 확장자를 치환하는
    // 방식이었으나, 이는 파일 경로 형식에 대한 암묵적 가정(fragile)이 있어
    // 각 형식별 독립적인 경로를 명시적으로 저장하도록 개선했습니다.

    /** 편곡 결과 MIDI 파일 경로. COMPLETE 상태일 때만 값이 있습니다. */
    @Column(name = "result_midi_path", length = 512)
    private String resultMidiPath;

    /** 편곡 결과 MusicXML 파일 경로 — 악보 뷰어에서 렌더링에 사용됩니다. */
    @Column(name = "result_xml_path", length = 512)
    private String resultXmlPath;

    /** 편곡 결과 PDF 파일 경로 — 인쇄용 악보 다운로드에 사용됩니다. */
    @Column(name = "result_pdf_path", length = 512)
    private String resultPdfPath;

    /** Soft Delete 시각. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 트랙-악기 매핑 목록.
     * "원본 트랙 0번을 → 피아노로 편곡"과 같은 정보를 담고 있습니다.
     */
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VersionMapping> mappings = new ArrayList<>();

    // ── Enum 정의 ──

    /**
     * 편곡 처리 상태 머신.
     * ArrangementService에서 AI 서버의 콜백을 받으면 이 상태가 전이됩니다.
     */
    public enum VersionStatus {
        /** 대기 중 — AI 서버에 요청을 보내기 전. */
        PENDING,
        /** 처리 중 — AI 서버가 편곡 작업을 수행 중. SSE로 진행률이 전송됩니다. */
        PROCESSING,
        /** 완료 — 결과 파일 다운로드 가능. */
        COMPLETE,
        /** 실패 — AI 서버 오류 또는 타임아웃. */
        FAILED
    }

    // ── 비즈니스 메서드 ──
    // setter 대신 의미 있는 도메인 메서드를 제공합니다.

    /** 버전 이름 변경. */
    public void rename(String newName) {
        this.name = newName;
    }

    /**
     * 상태 전이.
     * 상태 변경은 이 메서드를 통해서만 가능하며, Service 계층에서 호출합니다.
     */
    public void updateStatus(VersionStatus newStatus) {
        this.status = newStatus;
    }

    /** 
     * 진행률 업데이트 — AI 서버의 SSE 콜백에 의해 호출됩니다. 
     * 비동기 콜백 순서 역전(Out-of-order)으로 인해 진행률 바가 
     * 역행하는 것을 방지하기 위해 단방향(Monotonic) 증가만 허용합니다.
     */
    public void updateProgress(int progress) {
        if (progress > this.progress) {
            this.progress = progress;
        }
    }

    /**
     * 생성 설정 업데이트.
     * 재생성 시 사용자가 명시한 값만 업데이트하고, null이면 기존 값을 유지합니다.
     */
    public void updateGenerationSettings(Integer instrumentId, Integer minNote, Integer maxNote,
                                          Genre genre, Double temperature) {
        if (instrumentId != null) this.instrumentId = instrumentId;
        if (minNote != null) this.minNote = minNote;
        if (maxNote != null) this.maxNote = maxNote;
        if (genre != null) this.genre = genre;
        if (temperature != null) this.temperature = temperature;
    }

    /**
     * 결과 파일 경로를 한번에 업데이트합니다.
     * AI 서버가 편곡을 완료하면 MIDI, XML, PDF 세 가지 결과물의 경로를
     * 콜백 페이로드로 전달합니다.
     */
    public void updateResultPaths(String midiPath, String xmlPath, String pdfPath) {
        this.resultMidiPath = midiPath;
        this.resultXmlPath = xmlPath;
        this.resultPdfPath = pdfPath;
    }

    /** Soft Delete — 물리 삭제 대신 시각 기록. */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 편곡 완료 여부. 다운로드 요청 시 이 값을 확인합니다. */
    public boolean isComplete() {
        return this.status == VersionStatus.COMPLETE;
    }

    /** Soft Delete 여부. */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 요청된 파일 타입에 맞는 결과 파일 경로를 반환합니다.
     *
     * <p>
     * 이전에는 단일 결과 경로에서 확장자를 치환하는 방식이었으나,
     * DB v3.0에서 컬럼을 분리하면서 이 도메인 메서드로 대체했습니다.
     * Controller → Service → 이 메서드 순서로 호출됩니다.
     * </p>
     *
     * @param type "midi", "xml", "pdf" 중 하나
     * @return 해당 파일 경로, 지원하지 않는 타입이면 null
     */
    public String getResultPathByType(String type) {
        return switch (type.toLowerCase()) {
            case "midi" -> this.resultMidiPath;
            case "xml" -> this.resultXmlPath;
            case "pdf" -> this.resultPdfPath;
            default -> null;
        };
    }
}
