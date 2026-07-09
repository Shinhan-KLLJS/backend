package com.shinhan.klljs.domain.campaign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;

import java.time.LocalDate;

/**
 * 캠페인 목록 조회(GET /api/v1/dashboard/campaigns)의 캠페인 한 건.
 * 스펙 2절 "Response Fields"에 정의된 필드 그대로다.
 */
public record DashboardCampaignSummary(
        Long campaignId,
        String campaignName,
        String brandName,
        LocalDate executionStartDate,
        LocalDate executionEndDate,
        CampaignStatus status,

        // 주의: record 컴포넌트명을 그냥 isDefaultSelected로 쓰면 Jackson이 JavaBean 관례상
        // "is" 접두사를 벗겨내서 "defaultSelected"로 직렬화할 수 있다(버전/설정에 따라 달라짐).
        // 그래서 컴포넌트명은 평범하게 defaultSelected로 두고, @JsonProperty로 실제 응답 JSON
        // 키(isDefaultSelected)를 명시적으로 고정한다. ApiResponse.isSuccess()와 같은 패턴이다.
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
