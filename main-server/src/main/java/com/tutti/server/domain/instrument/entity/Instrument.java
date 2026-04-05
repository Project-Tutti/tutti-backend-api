package com.tutti.server.domain.instrument.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 악기 마스터 데이터 엔티티 (General MIDI 기준).
 *
 * <p>
 * 각 악기는 13개 카테고리 중 하나에 속하며,
 * 카테고리의 {@code isGeneratable} 플래그로 AI 생성 가능 여부가 결정됩니다.
 * </p>
 *
 * @see InstrumentCategory
 */
@Entity
@Table(name = "instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    @Id
    @Column(name = "midi_program")
    private Integer midiProgram;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** 소속 카테고리 — 13개 카테고리 중 하나. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "representative_program")
    private InstrumentCategory category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 이 악기의 기본 최소 MIDI 노트 번호. */
    @Column(name = "min_note")
    private Integer minNote;

    /** 이 악기의 기본 최대 MIDI 노트 번호. */
    @Column(name = "max_note")
    private Integer maxNote;
}
