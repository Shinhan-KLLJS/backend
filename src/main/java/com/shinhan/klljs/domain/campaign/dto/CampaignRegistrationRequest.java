package com.shinhan.klljs.domain.campaign.dto;

/**
 * 캠페인 등록 1~2단계에서 프론트가 보관한 값을 최종 제출하는 요청이다.
 * 날짜는 서비스가 yyyy-MM-dd를 직접 검증해 모든 날짜 오류를 CAMPAIGN_400_001로 통일한다.
 */
public record CampaignRegistrationRequest(
        String creativeToken,
        String campaignName,
        String brandName,
        String executionStartDate,
        String executionEndDate,
        Integer dailyTargetPlayCount,
        String description,
        Long mediaUnitId
) {
}
