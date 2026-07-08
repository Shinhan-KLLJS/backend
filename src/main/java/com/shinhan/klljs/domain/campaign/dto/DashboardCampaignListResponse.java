package com.shinhan.klljs.domain.campaign.dto;

import java.util.List;

public record DashboardCampaignListResponse(List<DashboardCampaignSummary> campaigns) {
    public static DashboardCampaignListResponse from(List<DashboardCampaignSummary> campaigns) {
        return new DashboardCampaignListResponse(campaigns);
    }
}
