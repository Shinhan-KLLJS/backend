package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;

/** 최종 등록된 캠페인의 화면 전환에 필요한 핵심 식별 정보다. */
public record CampaignRegistrationResponse(
        Long campaignId,
        Long teamId,
        String campaignName,
        CampaignStatus status,
        CampaignCreativeType creativeType,
        String creativeUrl,
        Long mediaUnitId
) {
    public static CampaignRegistrationResponse from(Campaign campaign, String creativeUrl) {
        return new CampaignRegistrationResponse(
                campaign.getId(),
                campaign.getTeam().getId(),
                campaign.getCampaignName(),
                campaign.getStatus(),
                campaign.getCreativeType(),
                creativeUrl,
                campaign.getMediaUnit().getId()
        );
    }
}
