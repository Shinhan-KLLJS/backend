package com.shinhan.klljs.domain.campaign.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignPlayCountEstimatorTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 10);
    private static final LocalDate END = LocalDate.of(2026, 7, 12);
    private static final int DAILY_TARGET = 200;

    @Test
    void estimateTodayPlayCount_returnsZeroBeforeExecutionStarts() {
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, END, DAILY_TARGET, LocalDate.of(2026, 7, 9), LocalDateTime.of(2026, 7, 9, 23, 0)
        );

        assertThat(result).isZero();
    }

    @Test
    void estimateTodayPlayCount_returnsDailyTargetAfterExecutionEnds() {
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, END, DAILY_TARGET, LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 13, 12, 0)
        );

        assertThat(result).isEqualTo(DAILY_TARGET);
    }

    @Test
    void estimateTodayPlayCount_returnsZeroBeforePlayStartTime() {
        // 오늘이 집행 기간 안이어도 06:00 전이면 아직 하나도 재생되지 않았다.
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, END, DAILY_TARGET, START, LocalDateTime.of(2026, 7, 10, 5, 59, 59)
        );

        assertThat(result).isZero();
    }

    @Test
    void estimateTodayPlayCount_estimatesElapsedIntervalsSincePlayStart() {
        // 06:00부터 정확히 150초(15초 x 10) 지났으면 10회.
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, END, DAILY_TARGET, START, LocalDateTime.of(2026, 7, 10, 6, 2, 30)
        );

        assertThat(result).isEqualTo(10);
    }

    @Test
    void estimateTodayPlayCount_capsAtDailyTargetEvenIfElapsedIntervalsExceedIt() {
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, END, DAILY_TARGET, START, LocalDateTime.of(2026, 7, 10, 23, 59, 59)
        );

        assertThat(result).isEqualTo(DAILY_TARGET);
    }

    @Test
    void estimateTodayPlayCount_treatsSingleDayCampaignBoundaryDatesAsInExecution() {
        // 집행 기간이 하루뿐이고(START == END) 오늘이 그 날이면 BEFORE/AFTER 어느 쪽도 아니다.
        int result = CampaignPlayCountEstimator.estimateTodayPlayCount(
                START, START, DAILY_TARGET, START, LocalDateTime.of(2026, 7, 10, 6, 0, 15)
        );

        assertThat(result).isEqualTo(1);
    }
}
