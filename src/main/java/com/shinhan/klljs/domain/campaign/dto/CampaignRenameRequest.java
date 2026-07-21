package com.shinhan.klljs.domain.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CampaignRenameRequest(
        @Schema(description = "캠페인명", example = "나이키 썸머 프로모션 2026")
        String campaignName
) {
}
