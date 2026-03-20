package com.tutti.server.domain.instrument.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 악기 마스터 데이터 엔티티 (General MIDI 기준).
 *
 * <ul>
 * <li>{@code is_active} — 프론트엔드에서 악기 목록에 표시할지 여부</li>
 * <li>{@code is_generatable} — AI 모델이 학습되어 편곡 생성이 가능한지 여부</li>
 * </ul>
 *
 * <p>
 * 새 악기 모델을 추가하면 DB에서 {@code is_generatable = true}로 설정하면 됩니다.
 * 코드 변경은 불필요합니다.
 * </p>
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

    @Column(length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_generatable", nullable = false)
    private boolean generatable = false;
}
