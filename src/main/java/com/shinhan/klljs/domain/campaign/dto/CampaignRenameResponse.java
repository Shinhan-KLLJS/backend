package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;

public record CampaignRenameResponse(
        Long campaignId,
        String campaignName
) {

    public static CampaignRenameResponse of(Campaign campaign) {
        return new CampaignRenameResponse(campaign.getId(), campaign.getCampaignName());
    }
}
