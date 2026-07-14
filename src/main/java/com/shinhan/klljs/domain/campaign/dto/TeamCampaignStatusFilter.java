package com.shinhan.klljs.domain.campaign.dto;

/**
 * GET /api/v1/teams/{teamId}/campaigns의 status 쿼리 파라미터 (campaign-page-api-spec.md 2-3절).
 * CampaignStatus를 그대로 쓰지 않고 별도 타입을 둔 이유: BEFORE_EXECUTION을 넘기면 REGISTERED도
 * 함께 포함해서 조회해야 하고, REGISTRATION_FAILED/REGISTERED는 화면에 노출되는 필터 값이 아니다.
 */
public enum TeamCampaignStatusFilter {
    BEFORE_EXECUTION,
    IN_EXECUTION,
    AFTER_EXECUTION
}
