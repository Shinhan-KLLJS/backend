package com.shinhan.klljs.domain.campaign.dto;

/** GET /api/v1/teams/{teamId}/campaigns의 sort 쿼리 파라미터 (campaign-page-api-spec.md 2-3절). */
public enum TeamCampaignSort {
    NAME,
    EXECUTION_RECENT,
    EXECUTION_OLDEST
}
