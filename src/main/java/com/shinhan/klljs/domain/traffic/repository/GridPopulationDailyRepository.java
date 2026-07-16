package com.shinhan.klljs.domain.traffic.repository;

import com.shinhan.klljs.domain.traffic.entity.GridPopulationDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GridPopulationDailyRepository extends JpaRepository<GridPopulationDaily, Long> {

    /** 적재 API의 upsert 대상 조회용 - 특정 날짜에 이미 적재된 격자를 한 번에 가져온다. */
    List<GridPopulationDaily> findByPopulationDate(LocalDate populationDate);

    /**
     * 깔대기 그래프(6절) 전체 유동인구용. 특정 격자코드의 [startDate, endDate](양끝 포함) 구간
     * 하루 합계를 전부 더한다. 시작~종료가 같은 날짜면 그 하루치 합계와 같다(어제 대비 증가율 계산에도 재사용).
     * 아직 적재되지 않은 날짜/격자는 coalesce로 0 처리한다.
     */
    @Query("""
            select coalesce(sum(g.totalTrafficCount), 0)
            from GridPopulationDaily g
            where g.gridCode = :gridCode
              and g.populationDate between :startDate and :endDate
            """)
    long sumTotalTrafficCount(
            @Param("gridCode") String gridCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
