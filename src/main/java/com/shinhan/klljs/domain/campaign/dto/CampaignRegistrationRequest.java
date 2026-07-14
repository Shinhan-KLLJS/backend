package com.shinhan.klljs.domain.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 캠페인 등록 1~2단계에서 프론트가 보관한 값을 최종 제출하는 요청이다.
 * 날짜는 서비스가 yyyy-MM-dd를 직접 검증해 모든 날짜 오류를 CAMPAIGN_400_001로 통일한다.
 */
public record CampaignRegistrationRequest(
        @Schema(description = "소재 업로드 URL 발급 시 함께 받은 서명 토큰", example = "v1.eyJjcmVhdGl2ZUlkIjo0Miwia2V5IjoiY2FtcGFpZ24tY3JlYXRpdmVzLzQyL2FiYzEyMyJ9.7f3a9c1e2b5d8f4a6c0e1b3d5f7a9c2e")
        String creativeToken,
        @Schema(description = "캠페인명", example = "나이키 썸머 프로모션 2026")
        String campaignName,
        @Schema(description = "브랜드명", example = "나이키 코리아")
        String brandName,
        @Schema(description = "집행 시작일 (yyyy-MM-dd)", example = "2026-07-11")
        String executionStartDate,
        @Schema(description = "집행 종료일 (yyyy-MM-dd)", example = "2026-08-15")
        String executionEndDate,
        @Schema(description = "하루 목표 송출 횟수", example = "200")
        Integer dailyTargetPlayCount,
        @Schema(description = "캠페인 설명", example = "브랜드 인지도 확대를 목표로 한 여름 프로모션 캠페인")
        String description,
        @Schema(description = "집행할 매체 ID", example = "12")
        Long mediaUnitId
) {
}
