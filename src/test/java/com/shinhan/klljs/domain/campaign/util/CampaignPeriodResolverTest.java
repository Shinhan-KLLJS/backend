package com.shinhan.klljs.domain.campaign.util;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * campaign_id + 기간을 받는 대시보드 API 전부(상세조회/송출정보/5-2/6/7절)가 공유하는
 * 핵심 유틸리티라, 순수 함수 단위로 직접 테스트해둔다 - 개별 서비스 테스트들은 대부분
 * BEFORE_EXECUTION/IN_EXECUTION만 다루고 AFTER_EXECUTION과 INVALID_PERIOD는 어디서도
 * 직접 검증하지 않고 있었다.
 */
class CampaignPeriodResolverTest {

    @Test
    void resolve_throwsInvalidPeriodWhenStartIsAfterEnd() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 5)))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode()).isEqualTo(CampaignErrorCode.INVALID_PERIOD));
    }

    @Test
    void resolve_beforeExecution_whenSelectedEndIsBeforeExecutionStart() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        CampaignPeriodContext context = CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(context.periodStatus()).isEqualTo(PeriodStatus.BEFORE_EXECUTION);
        assertThat(context.effectivePeriod()).isNull();
        assertThat(context.executionPeriod()).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void resolve_afterExecution_effectivePeriodIsWholeExecutionPeriodRegardlessOfSelected() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 선택 기간(8/1~8/7)과 무관하게 effectivePeriod는 집행 기간 전체(7/1~7/31)가 되어야 한다.
        CampaignPeriodContext context = CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(context.periodStatus()).isEqualTo(PeriodStatus.AFTER_EXECUTION);
        assertThat(context.effectivePeriod()).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        assertThat(context.selectedPeriod()).isEqualTo(new PeriodRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7)));
    }

    @Test
    void resolve_inExecution_effectivePeriodIsIntersectionOfSelectedAndExecution() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 선택 기간이 집행 시작일보다 하루 이르게 걸쳐 있는 경계 케이스 (스펙 0절 예시와 동일).
        CampaignPeriodContext context = CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2));

        assertThat(context.periodStatus()).isEqualTo(PeriodStatus.IN_EXECUTION);
        assertThat(context.effectivePeriod()).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)));
    }

    @Test
    void resolve_inExecution_exactlyOneDayOverlapBoundary() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 선택 종료일이 집행 시작일과 정확히 하루만 겹치는 경계값.
        CampaignPeriodContext onBoundary = CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 1));
        assertThat(onBoundary.periodStatus()).isEqualTo(PeriodStatus.IN_EXECUTION);
        assertThat(onBoundary.effectivePeriod()).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)));

        // 하루 차이로 안 겹치면 BEFORE_EXECUTION이어야 한다.
        CampaignPeriodContext justBefore = CampaignPeriodResolver.resolve(campaign, LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 30));
        assertThat(justBefore.periodStatus()).isEqualTo(PeriodStatus.BEFORE_EXECUTION);
    }

    @Test
    void resolveAggregationWindow_fullyPast_usesWholeEffectivePeriodAndDayAfterEndAsCutoff() {
        PeriodRange effectivePeriod = new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        LocalDate today = LocalDate.of(2026, 7, 7);
        LocalDateTime nowUtc = LocalDateTime.of(2026, 7, 7, 7, 43, 25);

        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.HOURS);

        assertThat(window.fromUtc()).isEqualTo(LocalDateTime.of(2026, 6, 30, 15, 0, 0)); // KST 7/1 00:00
        assertThat(window.toUtc()).isEqualTo(LocalDateTime.of(2026, 7, 3, 15, 0, 0)); // KST 7/4 00:00
        assertThat(window.cutoffTime()).isEqualTo(OffsetDateTime.of(2026, 7, 4, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void resolveAggregationWindow_todayIncluded_truncatesToInProgressUnitStart() {
        PeriodRange effectivePeriod = new PeriodRange(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7));
        LocalDate today = LocalDate.of(2026, 7, 7);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

        AggregationWindow minuteWindow = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.MINUTES);
        assertThat(minuteWindow.toUtc()).isEqualTo(LocalDateTime.of(2026, 7, 7, 7, 43, 0));
        assertThat(minuteWindow.cutoffTime()).isEqualTo(OffsetDateTime.of(2026, 7, 7, 16, 43, 0, 0, ZoneOffset.ofHours(9)));

        AggregationWindow hourWindow = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.HOURS);
        assertThat(hourWindow.toUtc()).isEqualTo(LocalDateTime.of(2026, 7, 7, 7, 0, 0));
        assertThat(hourWindow.cutoffTime()).isEqualTo(OffsetDateTime.of(2026, 7, 7, 16, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    private Campaign campaign(LocalDate executionStartDate, LocalDate executionEndDate) {
        return Campaign.builder()
                .team(null)
                .mediaUnit(null)
                .createdBy(null)
                .campaignName("캠페인A")
                .brandName("브랜드A")
                .executionStartDate(executionStartDate)
                .executionEndDate(executionEndDate)
                .dailyTargetPlayCount(1)
                .status(CampaignStatus.IN_EXECUTION)
                .build();
    }
}
