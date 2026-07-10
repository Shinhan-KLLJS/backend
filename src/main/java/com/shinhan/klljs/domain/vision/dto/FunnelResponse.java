package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 6. 깔대기 그래프 대시보드 조회 API(GET .../funnel)의 응답 바디. 스펙 6절 "Response Fields" 그대로다.
 *
 * 유동/노출/주목 인구는 "명" 단위 정수라 {@link PopulationMetric}을, 주목 전환률은 "%" 단위
 * 실수라 {@link RateMetric}을 쓴다 - 두 타입 모두 껍데기(value/unit/yesterdayComparison)는
 * 같지만 value/baseValue의 자바 타입이 Long이냐 Double이냐만 다르다.
 */
public record FunnelResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod, // BEFORE_EXECUTION이면 null
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        String aggregationUnit, // 항상 "MINUTE"
        OffsetDateTime aggregationCutoffTime, // 집계에 포함된 데이터의 배타적 상한 시각. BEFORE_EXECUTION이면 null
        int refreshIntervalSec, // 항상 60
        TrafficArea trafficArea,
        Metrics metrics
) {
    /**
     * 전체 유동인구를 매핑한 서울시 공공데이터 250m 구역 정보. MVP는 전체 유동인구용 DB 스키마를
     * 보류했으므로(스펙 9절 "전체 유동인구 DB 스키마") mock provider가 값을 채운다.
     * areaId/gridSizeMeter/dataSource/isMock/dataAggregationUnit은 캠페인이 집행 전이어도
     * (매체 위치 자체는 이미 알고 있으므로) 항상 채워지고, dataDateRange만 집행 전이면 null이다.
     */
    public record TrafficArea(
            String areaId,
            int gridSizeMeter, // 항상 250
            String dataSource, // 실제 공공데이터 연동 전까지 항상 "MOCK_SEOUL_OPEN_DATA"
            boolean isMock, // 실제 공공데이터 연동 전까지 항상 true
            String dataAggregationUnit, // 항상 "DAY"
            PeriodRange dataDateRange // 전체 유동인구 계산에 사용한 날짜 범위. 집행 전이면 null
    ) {
    }

    public record Metrics(
            PopulationMetric totalTrafficCount,
            PopulationMetric exposedPopulationCount,
            PopulationMetric attentionPopulationCount,
            RateMetric attentionConversionRate
    ) {
    }

    /** 유동/노출/주목 인구처럼 "명" 단위 정수로 응답하는 지표 공통 형태. */
    public record PopulationMetric(
            Long value, // 집행 전이면 null
            String unit, // 항상 "people"
            YesterdayComparison yesterdayComparison // 오늘 조회가 아니면 null
    ) {
        public record YesterdayComparison(LocalDate baseDate, Long baseValue, Double increaseRate) {
        }
    }

    /** 주목 전환률처럼 "%" 단위 실수로 응답하는 지표 형태. */
    public record RateMetric(
            Double value, // 노출인구가 0이거나 집행 전이면 null
            String unit, // 항상 "percent"
            YesterdayComparison yesterdayComparison // 오늘 조회가 아니면 null
    ) {
        public record YesterdayComparison(LocalDate baseDate, Double baseValue, Double increaseRate) {
        }
    }
}
