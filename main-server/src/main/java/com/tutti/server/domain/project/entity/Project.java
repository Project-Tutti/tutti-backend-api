package com.tutti.server.domain.project.entity;

import com.tutti.server.domain.user.entity.Profile;
import com.tutti.server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 엔티티 — DB의 "projects" 테이블에 매핑됩니다.
 *
 * <h3>도메인 개념</h3>
 * "프로젝트"는 사용자가 업로드한 하나의 MIDI 파일을 기반으로 만들어지는
 * 편곡 작업 단위입니다. 프로젝트 안에는 여러 "버전"(편곡 결과)이 포함됩니다.
 *
 * <h3>관계 구조</h3>
 * 
 * <pre>
 * Profile(사용자) 1 ──── N Project(프로젝트)
 * Project         1 ──── N ProjectVersion(편곡 버전)
 * Project         1 ──── N ProjectTrack(원본 트랙)
 * </pre>
 *
 * <h3>Soft Delete 정책</h3>
 * 프로젝트 삭제 시 {@code deletedAt}에 시각을 기록하는 논리 삭제를 사용합니다.
 * Repository의 조회 쿼리에서 {@code deletedAt IS NULL} 조건으로 필터링합니다.
 *
 * @see ProjectVersion
 * @see ProjectTrack
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Project extends BaseTimeEntity {

    /**
     * PK — DB의 auto-increment(IDENTITY 전략)로 자동 생성됩니다.
     *
     * @GeneratedValue(IDENTITY): PostgreSQL의 SERIAL/BIGSERIAL에 매핑.
     * DB INSERT 시점에 ID가 할당됩니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 프로젝트 소유자 — Profile 엔티티와 다대일(N:1) 관계.
     *
     * @ManyToOne(LAZY): 프로젝트를 조회할 때 Profile을 즉시 로드하지 않습니다.
     * 실제로 project.getUser()를 호출할 때만 DB 쿼리가 실행됩니다.
     * 왜? → 프로젝트 목록 조회 시 불필요한 JOIN을 방지하여 성능을 최적화합니다.
     * 
     * @JoinColumn: DB에서 외래키(FK) 컬럼명을 "user_id"로 지정합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    /** 사용자가 지정한 프로젝트 이름 (UI에 표시). */
    @Column(nullable = false, length = 100)
    private String name;

    /** 업로드된 원본 MIDI 파일명 (예: "moonlight_sonata.mid"). */
    @Column(nullable = false, length = 255)
    private String originalFileName;

    /** 서버에 저장된 MIDI 파일 경로 (예: "uploads/midi/{userId}/{uuid}_filename.mid"). */
    @Column(length = 512)
    private String midiFilePath;

    /** Soft Delete 시각 — null이면 활성 프로젝트. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 이 프로젝트에 속한 편곡 버전 목록.
     *
     * @OneToMany(mappedBy): ProjectVersion 엔티티의 "project" 필드가 연관관계의 주인.
     * → DB FK는 project_versions 테이블에 있습니다.
     * cascade = ALL: 프로젝트 저장/삭제 시 버전도 함께 처리됩니다.
     * orphanRemoval = true: 컬렉션에서 제거된 버전은 DB에서도 삭제됩니다.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectVersion> versions = new ArrayList<>();

    /**
     * 이 프로젝트의 원본 MIDI 트랙 목록.
     * MIDI 파일을 파싱하여 추출된 각 트랙(피아노, 기타 등)의 정보를 저장합니다.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectTrack> tracks = new ArrayList<>();

    // ── 비즈니스 메서드 ──

    /**
     * 프로젝트 이름 변경.
     * setter를 열지 않고 명시적인 도메인 메서드를 제공하여,
     * "이름 변경"이라는 비즈니스 의도를 코드에 드러냅니다.
     */
    public void rename(String newName) {
        this.name = newName;
    }

    /**
     * 프로젝트 삭제 (Soft Delete).
     * 왜 물리 삭제가 아닌가? → 사용자가 실수로 삭제한 경우 복구 가능성을 남기고,
     * 관련된 편곡 결과 파일을 비동기로 정리하기 위함입니다.
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** Soft Delete 여부 확인. deletedAt이 설정되어 있으면 삭제된 것으로 간주합니다. */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
