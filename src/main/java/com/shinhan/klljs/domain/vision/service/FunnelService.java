package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.traffic.repository.GridPopulationDailyRepository;
import com.shinhan.klljs.domain.traffic.util.GridCodeCalculator;
import com.shinhan.klljs.domain.traffic.util.PopulationDataLag;
import com.shinhan.klljs.domain.vision.dto.FunnelResponse;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository.PopulationSums;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 6. 깔대기 그래프 대시보드 조회 API(스펙 6절)를 처리한다.
 *
 * 전체 유동인구/노출인구/주목인구/주목 전환률 4개 지표와, "오늘" 조회일 때만 어제 같은
 * 시간대 대비 증가율을 함께 응답한다. 전체 유동인구는 매체 위경도로 계산한 격자코드의
 * grid_population_daily(서울시 250m격자 생활인구, PopulationIngestService가 적재)를
 * {@link PopulationDataLag}만큼 시프트해서 조회한다.
 */
@Service
@RequiredArgsConstructor
public class FunnelService {

    private static final String AGGREGATION_UNIT = "MINUTE";
    private static final int REFRESH_INTERVAL_SEC = 60;
    private static final int TRAFFIC_GRID_SIZE_METER = 250;
    private static final String TRAFFIC_DATA_SOURCE = "SEOUL_OPEN_DATA";
    private static final String TRAFFIC_DATA_AGGREGATION_UNIT = "DAY";

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final GridPopulationDailyRepository gridPopulationDailyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FunnelResponse getFunnel(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);

        // BEFORE_EXECUTION: 집행 전이라 집계할 데이터가 없다 (스펙 0절 기간 처리 규칙).
        // trafficArea는 매체 위치 정보라 집행 여부와 무관하게 채우고, dataDateRange만 null로 둔다.
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new FunnelResponse(
                    campaign.getId(), periodContext.selectedPeriod(), null, periodContext.periodStatus(),
                    serverTime, AGGREGATION_UNIT, null, REFRESH_INTERVAL_SEC,
                    trafficArea(campaign, null),
                    emptyMetrics()
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        LocalDate today = KstDateTimes.todayKst(nowUtc);

        // 조회 범위/커서 계산은 5-2/6/7절이 전부 공유하는 규칙이라 CampaignPeriodResolver로 뺐다.
        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.MINUTES);
        LocalDateTime fromUtc = window.fromUtc();
        LocalDateTime toUtc = window.toUtc();

        PopulationSums todaySums = visionSummary5sRepository.sumPopulationInRange(campaign.getId(), fromUtc, toUtc);

        // 스펙 6절 "어제 대비 증가율": selected_start_date == selected_end_date == 오늘일 때만 계산한다.
        // 단일 날짜(오늘~오늘)로 선택 기간이 execution 기간과 겹쳐야 하므로 IN_EXECUTION도 같이 확인한다
        // (AFTER_EXECUTION인데 우연히 오늘 날짜를 선택한 경우는 effectivePeriod가 "오늘"이 아니라
        // 캠페인 집행 기간 전체가 되므로, "오늘 대비 어제" 비교 자체가 의미가 없어서 제외한다).
        boolean isTodayQuery = periodContext.periodStatus() == PeriodStatus.IN_EXECUTION
                && selectedStartDate.equals(today) && selectedEndDate.equals(today);

        PopulationSums yesterdaySums = isTodayQuery
                ? visionSummary5sRepository.sumPopulationInRange(campaign.getId(), fromUtc.minusDays(1), toUtc.minusDays(1))
                : null;
        LocalDate yesterdayDate = today.minusDays(1);

        long exposedToday = todaySums.exposedPopulationCount();
        long attentionToday = todaySums.attentionPopulationCount();
        Long exposedYesterday = yesterdaySums == null ? null : yesterdaySums.exposedPopulationCount();
        Long attentionYesterday = yesterdaySums == null ? null : yesterdaySums.attentionPopulationCount();

        FunnelResponse.PopulationMetric exposedMetric = populationMetric(exposedToday, exposedYesterday, yesterdayDate);
        FunnelResponse.PopulationMetric attentionMetric = populationMetric(attentionToday, attentionYesterday, yesterdayDate);

        Double conversionToday = conversionRate(attentionToday, exposedToday);
        Double conversionYesterday = yesterdaySums == null ? null : conversionRate(attentionYesterday, exposedYesterday);
        FunnelResponse.RateMetric conversionMetric = rateMetric(conversionToday, conversionYesterday, yesterdayDate);

        FunnelResponse.PopulationMetric totalTrafficMetric =
                totalTrafficMetric(campaign, effectivePeriod, isTodayQuery, yesterdayDate);

        return new FunnelResponse(
                campaign.getId(), periodContext.selectedPeriod(), effectivePeriod, periodContext.periodStatus(),
                serverTime, AGGREGATION_UNIT, window.cutoffTime(), REFRESH_INTERVAL_SEC,
                trafficArea(campaign, effectivePeriod),
                new FunnelResponse.Metrics(totalTrafficMetric, exposedMetric, attentionMetric, conversionMetric)
        );
    }

    /**
     * 유동/노출/주목 인구 공통 계산. yesterdayValue가 null이면(오늘 조회가 아니면) 비교값 자체를 안 내려주고,
     * yesterdayValue가 0이면(0으로 나눌 수 없음) increaseRate만 null로 응답한다 (스펙 6절 "어제 대비 증가율").
     */
    private FunnelResponse.PopulationMetric populationMetric(long todayValue, Long yesterdayValue, LocalDate yesterdayDate) {
        if (yesterdayValue == null) {
            return new FunnelResponse.PopulationMetric(todayValue, "people", null);
        }
        Double increaseRate = yesterdayValue == 0 ? null : round1((todayValue - yesterdayValue) * 100.0 / yesterdayValue);
        var comparison = new FunnelResponse.PopulationMetric.YesterdayComparison(yesterdayDate, yesterdayValue, increaseRate);
        return new FunnelResponse.PopulationMetric(todayValue, "people", comparison);
    }

    /** attentionConversionRate = attentionCount / exposedCount * 100. exposedCount가 0이면 계산 불가하므로 null. */
    private Double conversionRate(long attentionCount, long exposedCount) {
        return exposedCount == 0 ? null : round1(attentionCount * 100.0 / exposedCount);
    }

    /**
     * 주목 전환률의 어제 대비 증가율. todayValue/yesterdayValue 둘 중 하나라도 null이면(각각
     * 노출인구가 0이라 전환률 자체가 없거나, 오늘 조회가 아니면) 비교값 전체를 null로 응답한다.
     */
    private FunnelResponse.RateMetric rateMetric(Double todayValue, Double yesterdayValue, LocalDate yesterdayDate) {
        if (todayValue == null || yesterdayValue == null) {
            return new FunnelResponse.RateMetric(todayValue, "percent", null);
        }
        Double increaseRate = yesterdayValue == 0 ? null : round1((todayValue - yesterdayValue) * 100.0 / yesterdayValue);
        var comparison = new FunnelResponse.RateMetric.YesterdayComparison(yesterdayDate, yesterdayValue, increaseRate);
        return new FunnelResponse.RateMetric(todayValue, "percent", comparison);
    }

    /** 퍼센트 값은 화면에 소수점 첫째 자리까지만 노출한다 (소수점 둘째 자리에서 반올림, issue #46). */
    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 전체 유동인구. 매체 위경도로 계산한 격자코드의 grid_population_daily 하루 합계를,
     * effectivePeriod를 {@link PopulationDataLag#DAYS}만큼 과거로 시프트한 구간 전체에 대해
     * 더한다 (발행 지연 6일 매핑 - 스펙 9절 "전체 유동인구 DB 스키마" 항목 해소).
     * 어제 대비 증가율도 같은 방식으로 "어제-6일" 하루치를 조회해서 계산한다.
     */
    private FunnelResponse.PopulationMetric totalTrafficMetric(
            Campaign campaign, PeriodRange effectivePeriod, boolean isTodayQuery, LocalDate yesterdayDate
    ) {
        String gridCode = gridCode(campaign);

        long todayValue = gridPopulationDailyRepository.sumTotalTrafficCount(
                gridCode,
                effectivePeriod.startDate().minusDays(PopulationDataLag.DAYS),
                effectivePeriod.endDate().minusDays(PopulationDataLag.DAYS)
        );

        Long yesterdayValue = null;
        if (isTodayQuery) {
            LocalDate laggedYesterday = yesterdayDate.minusDays(PopulationDataLag.DAYS);
            yesterdayValue = gridPopulationDailyRepository.sumTotalTrafficCount(gridCode, laggedYesterday, laggedYesterday);
        }

        return populationMetric(todayValue, yesterdayValue, yesterdayDate);
    }

    /** dataDateRange만 상황에 따라 다르고(집행 전이면 null) 나머지 필드는 항상 고정값이라 공용으로 뺐다. */
    private FunnelResponse.TrafficArea trafficArea(Campaign campaign, PeriodRange dataDateRange) {
        return new FunnelResponse.TrafficArea(
                gridCode(campaign),
                TRAFFIC_GRID_SIZE_METER,
                TRAFFIC_DATA_SOURCE,
                false,
                TRAFFIC_DATA_AGGREGATION_UNIT,
                dataDateRange
        );
    }

    private String gridCode(Campaign campaign) {
        MediaUnit mediaUnit = campaign.getMediaUnit();
        return GridCodeCalculator.calculate(mediaUnit.getLatitude(), mediaUnit.getLongitude());
    }

    private FunnelResponse.Metrics emptyMetrics() {
        FunnelResponse.PopulationMetric emptyPopulation = new FunnelResponse.PopulationMetric(null, "people", null);
        FunnelResponse.RateMetric emptyRate = new FunnelResponse.RateMetric(null, "percent", null);
        return new FunnelResponse.Metrics(emptyPopulation, emptyPopulation, emptyPopulation, emptyRate);
    }
}
