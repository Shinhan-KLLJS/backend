package com.shinhan.klljs.domain.campaign.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 캠페인 송출정보 조회(GET /api/v1/dashboard/campaigns/{campaign_id}/delivery)의 응답 바디.
 * 스펙 4절 "Response Fields" 그대로다. currentPlayCount/progressRate/totalPlayTimeMin/
 * nextIncrementAt은 BEFORE_EXECUTION일 때만 null이고, 그 외에는 항상 값이 있다.
 */
public record DashboardCampaignDeliveryResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod,
        PeriodStatus periodStatus,
        LocalDate today,
        OffsetDateTime serverTime,
        LocalTime playStartTime,
        int playIntervalSec,
        int refreshIntervalSec,
        Integer currentPlayCount,
        int dailyTargetPlayCount,
        Integer periodTargetPlayCount,
        Double progressRate,
        Integer totalPlayTimeMin,
        OffsetDateTime nextIncrementAt,
        boolean isEstimated
) {
}
