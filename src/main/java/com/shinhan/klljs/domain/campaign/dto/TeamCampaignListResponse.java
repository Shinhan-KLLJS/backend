package com.shinhan.klljs.domain.campaign.dto;

import java.util.List;

/** GET /api/v1/teams/{teamId}/campaigns 최상위 응답 바디 (campaign-page-api-spec.md 4절 Response Example). */
public record TeamCampaignListResponse(String teamName, List<TeamCampaignSummary> campaigns) {
    public static TeamCampaignListResponse from(String teamName, List<TeamCampaignSummary> campaigns) {
        return new TeamCampaignListResponse(teamName, campaigns);
    }
}
