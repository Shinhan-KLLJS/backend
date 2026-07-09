package com.shinhan.klljs.domain.campaign.dto;

import java.util.List;

/**
 * GET /api/v1/dashboard/campaigns 최상위 응답 바디.
 * 스펙 응답 예시가 배열을 바로 내려주지 않고 { "campaigns": [...] } 형태로 한 번 감싸므로
 * 그에 맞춰 래퍼 record를 둔다.
 */
public record DashboardCampaignListResponse(List<DashboardCampaignSummary> campaigns) {
    public static DashboardCampaignListResponse from(List<DashboardCampaignSummary> campaigns) {
        return new DashboardCampaignListResponse(campaigns);
    }
}
