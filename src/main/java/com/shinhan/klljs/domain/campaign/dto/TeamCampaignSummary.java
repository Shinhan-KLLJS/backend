package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;

import java.time.LocalDate;

/**
 * GET /api/v1/teams/{teamId}/campaigns 목록의 캠페인 한 건 (campaign-page-api-spec.md 4절 Response Fields).
 * status는 REGISTERED를 포함해 DB에 저장된 값 그대로 내려간다 - 프론트가 REGISTERED를
 * "집행 전"과 같은 배지로 표시한다.
 */
public record TeamCampaignSummary(
        Long campaignId,
        String campaignName,
        CampaignStatus status,
        LocalDate executionStartDate,
        LocalDate executionEndDate,
        String mediaLocationAddress,
        int todayPlayCount,
        int dailyTargetPlayCount
) {
    public static TeamCampaignSummary from(Campaign campaign, int todayPlayCount) {
        return new TeamCampaignSummary(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getStatus(),
                campaign.getExecutionStartDate(),
                campaign.getExecutionEndDate(),
                campaign.getMediaUnit().getLocationAddress(),
                todayPlayCount,
                campaign.getDailyTargetPlayCount()
        );
    }
}
