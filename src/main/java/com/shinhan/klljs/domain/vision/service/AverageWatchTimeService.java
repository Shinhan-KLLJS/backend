package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.vision.dto.AverageWatchTimeResponse;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository.WatchTimeSums;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * 7. 평균 시청시간 API(스펙 7절)를 처리한다.
 *
 * 1분 단위로 확정된 데이터만 집계한다는 점을 빼면, 계산 자체는 캠페인의 전체 유동/노출/주목
 * 인구와 비슷하게 "구간 합계"로 끝난다 - 5초 window별 avg_dwell_sec을 단순 평균하지 않고,
 * dwell_sum_sec/lts_count 가중평균을 쓴다(스펙 7절 "이 식은 ... 전제로 성립한다" 문단 참고 -
 * Vision AI팀 확인이 아직 필요한 전제라는 점에 유의).
 */
@Service
@RequiredArgsConstructor
public class AverageWatchTimeService {

    private static final String AGGREGATION_UNIT = "MINUTE";
    private static final int REFRESH_INTERVAL_SEC = 60;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AverageWatchTimeResponse getAverageWatchTime(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);

        // BEFORE_EXECUTION: 집행 전이라 집계할 데이터가 없다 (스펙 0절 기간 처리 규칙).
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new AverageWatchTimeResponse(
                    campaign.getId(), periodContext.selectedPeriod(), null, periodContext.periodStatus(),
                    serverTime, AGGREGATION_UNIT, null, REFRESH_INTERVAL_SEC, null, Collections.emptyList()
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        LocalDate today = KstDateTimes.todayKst(nowUtc);
        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.MINUTES);

        WatchTimeSums sums = visionSummary5sRepository.sumWatchTimeInRange(campaign.getId(), window.fromUtc(), window.toUtc());

        // averageWatchTimeSec = sum(dwell_sum_sec) / sum(lts_count). 분모가 0이면 계산 불가하므로 null.
        Double averageWatchTimeSec = sums.ltsCount() == 0 ? null : sums.dwellSumSec().doubleValue() / sums.ltsCount();

        return new AverageWatchTimeResponse(
                campaign.getId(), periodContext.selectedPeriod(), effectivePeriod, periodContext.periodStatus(),
                serverTime, AGGREGATION_UNIT, window.cutoffTime(), REFRESH_INTERVAL_SEC,
                averageWatchTimeSec, buildBuckets(sums)
        );
    }

    /** 시청시간 구간별 분포. ratio는 4개 구간 합계 대비 비율(%)이라 4개를 더하면 항상 100%가 된다 (합계가 0이 아닌 한). */
    private List<AverageWatchTimeResponse.WatchTimeBucket> buildBuckets(WatchTimeSums sums) {
        long total = sums.dwell1To2s() + sums.dwell2To3s() + sums.dwell3To4s() + sums.dwellOver4s();
        return List.of(
                bucket("1_TO_2S", "1-2초", sums.dwell1To2s(), total),
                bucket("2_TO_3S", "2-3초", sums.dwell2To3s(), total),
                bucket("3_TO_4S", "3-4초", sums.dwell3To4s(), total),
                bucket("OVER_4S", "4초 이상", sums.dwellOver4s(), total)
        );
    }

    private AverageWatchTimeResponse.WatchTimeBucket bucket(String code, String label, long count, long total) {
        double ratio = total == 0 ? 0.0 : count * 100.0 / total;
        return new AverageWatchTimeResponse.WatchTimeBucket(code, label, count, ratio);
    }
}
