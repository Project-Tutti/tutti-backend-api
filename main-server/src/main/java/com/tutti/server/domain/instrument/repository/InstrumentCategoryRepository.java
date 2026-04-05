package com.tutti.server.domain.instrument.repository;

import com.tutti.server.domain.instrument.entity.InstrumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InstrumentCategoryRepository extends JpaRepository<InstrumentCategory, Integer> {

    /** 전체 카테고리 목록 (representative_program 오름차순). */
    List<InstrumentCategory> findAllByOrderByRepresentativeProgramAsc();

    /** 생성 가능한 카테고리만 반환 (세부 악기 포함). */
    @Query("SELECT DISTINCT c FROM InstrumentCategory c " +
            "LEFT JOIN FETCH c.instruments i " +
            "WHERE c.generatable = true " +
            "ORDER BY c.representativeProgram")
    List<InstrumentCategory> findGeneratableWithInstruments();

    /** 특정 카테고리가 생성 가능한지 확인. */
    boolean existsByRepresentativeProgramAndGeneratableTrue(Integer representativeProgram);
}
