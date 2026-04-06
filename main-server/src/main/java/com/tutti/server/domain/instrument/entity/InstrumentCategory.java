package com.tutti.server.domain.instrument.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 악기 카테고리 마스터 데이터 엔티티.
 *
 * <p>
 * 128개 MIDI 악기를 14개 카테고리로 그룹화합니다.
 * 각 카테고리에는 대표 MIDI 프로그램 번호가 있으며,
 * {@code isGeneratable}로 AI 생성 가능 여부를 표시합니다.
 * Drop(129) 카테고리는 AI 매핑 시 트랙을 제거하는 용도입니다.
 * </p>
 *
 * @see Instrument
 */
@Entity
@Table(name = "instrument_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentCategory {

    /**
     * 대표 MIDI 프로그램 번호 — PK이자 카테고리 식별자.
     * 예: 0=Keyboard, 40=Solo String, 128=Drum, 129=Drop
     */
    @Id
    @Column(name = "representative_program")
    private Integer representativeProgram;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * AI 편곡 생성 가능 여부.
     * 현재 Solo String(40), Brass(56), Saxophone(65), Woodwind(73)만 true.
     */
    @Column(name = "is_generatable", nullable = false)
    private boolean generatable = false;

    /** 이 카테고리에 속하는 개별 악기 목록. */
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Instrument> instruments = new ArrayList<>();

    /** 드럼 카테고리 여부 — representative_program == 128이면 드럼. */
    public boolean isDrum() {
        return this.representativeProgram == 128;
    }

    /** Drop 카테고리 여부 — representative_program == 129이면 트랙 제거 대상. */
    public boolean isDrop() {
        return this.representativeProgram == 129;
    }
}
