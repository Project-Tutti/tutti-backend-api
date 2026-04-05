package com.tutti.server.domain.instrument.repository;

import com.tutti.server.domain.instrument.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {

    /** 활성 악기 전체 목록 (카테고리 정보 포함). */
    @Query("SELECT i FROM Instrument i JOIN FETCH i.category " +
            "WHERE i.active = true ORDER BY i.midiProgram")
    List<Instrument> findByActiveTrueOrderByMidiProgram();

    /** 특정 카테고리에 속한 활성 악기 목록. */
    @Query("SELECT i FROM Instrument i " +
            "WHERE i.active = true AND i.category.representativeProgram = :categoryId " +
            "ORDER BY i.midiProgram")
    List<Instrument> findByCategoryId(@Param("categoryId") Integer categoryId);
}
