package com.shinhan.klljs.domain.traffic.entity;

import com.shinhan.klljs.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 서울시 250m격자 생활인구(내국인)를 격자코드+날짜 단위 하루 합계로 저장한다. */
@Entity
@Table(name = "grid_population_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GridPopulationDaily extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grid_code", nullable = false, length = 20)
    private String gridCode;

    @Column(name = "population_date", nullable = false)
    private LocalDate populationDate;

    @Column(name = "total_traffic_count", nullable = false)
    private Long totalTrafficCount;

    @Builder
    public GridPopulationDaily(String gridCode, LocalDate populationDate, Long totalTrafficCount) {
        this.gridCode = gridCode;
        this.populationDate = populationDate;
        this.totalTrafficCount = totalTrafficCount;
    }

    public void updateTotalTrafficCount(Long totalTrafficCount) {
        this.totalTrafficCount = totalTrafficCount;
    }
}
