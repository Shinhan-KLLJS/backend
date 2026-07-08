package com.shinhan.klljs.domain.campaign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;

import java.time.LocalDate;

public record DashboardCampaignSummary(
        Long campaignId,
        String campaignName,
        String brandName,
        LocalDate executionStartDate,
        LocalDate executionEndDate,
        CampaignStatus status,
        @JsonProperty("isDefaultSelected") boolean defaultSelected
) {
    public static DashboardCampaignSummary from(Campaign campaign, boolean defaultSelected) {
        return new DashboardCampaignSummary(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getBrandName(),
                campaign.getExecutionStartDate(),
                campaign.getExecutionEndDate(),
                campaign.getStatus(),
                defaultSelected
        );
    }
}
