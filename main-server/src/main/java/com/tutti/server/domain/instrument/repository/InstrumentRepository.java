package com.tutti.server.domain.instrument.repository;

import com.tutti.server.domain.instrument.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {

    /** 활성 악기 전체 목록 (프론트엔드 악기 선택 UI). */
    List<Instrument> findByActiveTrueOrderByMidiProgram();

    /** AI 생성 가능한 악기만 반환 (편곡 대상 악기 선택 UI). */
    List<Instrument> findByActiveTrueAndGeneratableTrueOrderByMidiProgram();

    /** 특정 악기가 생성 가능한지 확인. */
    boolean existsByMidiProgramAndGeneratableTrue(Integer midiProgram);
}
