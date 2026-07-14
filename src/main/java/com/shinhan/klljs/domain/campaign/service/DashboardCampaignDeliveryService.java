package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDeliveryResponse;
import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.campaign.util.CampaignPlayCountEstimator;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 캠페인 송출정보 조회(스펙 4절)를 처리한다.
 *
 * 실제 송출 로그 테이블이 아직 없어서(스펙 9절 "MVP 보류 항목"), currentPlayCount는 전부
 * "06:00부터 15초 간격으로 다운타임 없이 재생됐다"는 가정 하의 추정치다(isEstimated 항상 true).
 * 실제 로그 테이블이 생기면 estimatePlayCount() 내부 계산만 로그 집계 쿼리로 교체하면 되고,
 * 그 외 기간 처리/응답 조립 로직은 그대로 재사용할 수 있도록 분리해뒀다.
 */
@Service
@RequiredArgsConstructor
public class DashboardCampaignDeliveryService {

    private static final LocalTime PLAY_START_TIME = CampaignPlayCountEstimator.PLAY_START_TIME;
    private static final int PLAY_INTERVAL_SEC = CampaignPlayCountEstimator.PLAY_INTERVAL_SEC;
    private static final int REFRESH_INTERVAL_SEC = 15;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardCampaignDeliveryResponse getDelivery(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        LocalDateTime nowKst = KstDateTimes.toKst(nowUtc);
        LocalDate today = nowKst.toLocalDate();
        int dailyTarget = campaign.getDailyTargetPlayCount();

        // BEFORE_EXECUTION: 아직 아무것도 집행되지 않았으므로 집계형 필드는 전부 null.
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new DashboardCampaignDeliveryResponse(
                    campaign.getId(),
                    periodContext.selectedPeriod(),
                    null,
                    periodContext.periodStatus(),
                    today,
                    KstDateTimes.toKstOffset(nowUtc),
                    PLAY_START_TIME,
                    PLAY_INTERVAL_SEC,
                    REFRESH_INTERVAL_SEC,
                    null,
                    dailyTarget,
                    null,
                    null,
                    null,
                    null,
                    true
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        int periodDays = (int) (ChronoUnit.DAYS.between(effectivePeriod.startDate(), effectivePeriod.endDate()) + 1);
        int periodTargetPlayCount = dailyTarget * periodDays;

        PlayCountEstimate estimate = estimatePlayCount(effectivePeriod, today, nowKst, dailyTarget);

        int totalPlayTimeMin = (PLAY_INTERVAL_SEC * estimate.playCount()) / 60;
        double progressRate = periodTargetPlayCount == 0 ? 0.0 : (estimate.playCount() * 100.0 / periodTargetPlayCount);

        return new DashboardCampaignDeliveryResponse(
                campaign.getId(),
                periodContext.selectedPeriod(),
                effectivePeriod,
                periodContext.periodStatus(),
                today,
                KstDateTimes.toKstOffset(nowUtc),
                PLAY_START_TIME,
                PLAY_INTERVAL_SEC,
                REFRESH_INTERVAL_SEC,
                estimate.playCount(),
                dailyTarget,
                periodTargetPlayCount,
                progressRate,
                totalPlayTimeMin,
                estimate.nextIncrementAtKst() == null ? null : estimate.nextIncrementAtKst().atOffset(KstDateTimes.KST),
                true
        );
    }

    /**
     * 실제 송출 로그가 없는 상태에서 currentPlayCount를 추정한다. 선택된(effective) 기간이
     * 오늘 기준으로 어디에 있는지에 따라 세 갈래로 나뉜다:
     *
     * 1) 기간 전체가 미래(effStart > today): 아직 시작 전이므로 0회. 다음 증가 시각 개념도 없다.
     * 2) 기간 전체가 과거(effEnd < today): 확정된 날들이므로, 매일 다운타임 없이
     *    목표치를 100% 채웠다고 가정하고 dailyTarget * 일수로 계산한다.
     * 3) 오늘이 기간에 포함(effStart <= today <= effEnd): 오늘 이전 날짜는 2)와 같이
     *    목표치 100% 완료로 가정하고, 오늘 몫만 06:00부터 15초 간격 시간 기반으로
     *    실시간 추정한다 (스펙 "카운트 갱신 권장안"이 이 값을 기준으로 프론트에서 로컬 증가시킨다).
     *
     * 기간이 오늘을 넘어 미래까지 걸쳐 있어도(예: 진행 중인 캠페인에서 미래 날짜까지 선택),
     * 오늘 이후 몫은 아직 일어나지 않았으므로 세지 않는다 - effectivePeriod의 끝이 아니라
     * "오늘까지"만 계산 대상으로 삼는 게 이 메서드의 핵심이다.
     */
    private PlayCountEstimate estimatePlayCount(PeriodRange effectivePeriod, LocalDate today, LocalDateTime nowKst, int dailyTarget) {
        LocalDate effectiveStartDate = effectivePeriod.startDate();
        LocalDate effectiveEndDate = effectivePeriod.endDate();

        if (effectiveStartDate.isAfter(today)) {
            return new PlayCountEstimate(0, null);
        }

        if (effectiveEndDate.isBefore(today)) {
            long fullDays = ChronoUnit.DAYS.between(effectiveStartDate, effectiveEndDate) + 1;
            return new PlayCountEstimate((int) (dailyTarget * fullDays), null);
        }

        // 오늘이 포함된 경우: 오늘 이전 날짜만큼은 확정치로 더하고, 오늘 몫은 시간 기반으로 추정한다.
        long fullDaysBeforeToday = ChronoUnit.DAYS.between(effectiveStartDate, today);
        long completedPlayCount = dailyTarget * fullDaysBeforeToday;

        LocalDateTime todayStartKst = today.atTime(PLAY_START_TIME);
        long elapsedSec = Math.max(0, Duration.between(todayStartKst, nowKst).getSeconds());
        long todayRawEstimate = elapsedSec / PLAY_INTERVAL_SEC;
        long todayEstimate = Math.min(todayRawEstimate, dailyTarget);

        int playCount = (int) (completedPlayCount + todayEstimate);

        // 아직 오늘 목표치에 도달하지 않았을 때만 "다음 증가 시각"이 있다.
        // 목표 도달(todayRawEstimate >= dailyTarget) 또는 애초에 오늘이 기간에 없으면 null.
        LocalDateTime nextIncrementAtKst = null;
        if (todayRawEstimate < dailyTarget) {
            nextIncrementAtKst = todayStartKst.plusSeconds((todayRawEstimate + 1) * PLAY_INTERVAL_SEC);
        }

        return new PlayCountEstimate(playCount, nextIncrementAtKst);
    }

    private record PlayCountEstimate(int playCount, LocalDateTime nextIncrementAtKst) {
    }
}
