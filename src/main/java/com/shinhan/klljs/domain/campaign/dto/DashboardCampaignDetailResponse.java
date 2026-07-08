package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;

public record DashboardCampaignDetailResponse(
        Long campaignId,
        String campaignName,
        String brandName,
        String description,
        String imageUrl,
        CampaignStatus status,
        Long mediaUnitId,
        Integer dailyTargetPlayCount,
        PeriodRange executionPeriod,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod,
        PeriodStatus periodStatus
) {
    public static DashboardCampaignDetailResponse from(Campaign campaign, CampaignPeriodContext periodContext) {
        return new DashboardCampaignDetailResponse(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getBrandName(),
                campaign.getDescription(),
                campaign.getImageUrl(),
                campaign.getStatus(),
                campaign.getMediaUnit().getId(),
                campaign.getDailyTargetPlayCount(),
                periodContext.executionPeriod(),
                periodContext.selectedPeriod(),
                periodContext.effectivePeriod(),
                periodContext.periodStatus()
        );
    }
}
