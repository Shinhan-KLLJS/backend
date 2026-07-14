package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/teams/{teamId}/campaigns/{campaignId} 응답 바디 (campaign-page-api-spec.md 5절).
 * DashboardCampaignDetailResponse와 달리 매체 조인 정보(mediaName/규격/해상도/형태)와
 * creativeUrl을 포함한다 - 이 API는 등록 3단계 "최종 확인" 화면과 같은 내용을 보여주는 게 목적이다.
 */
public record TeamCampaignDetailResponse(
        Long campaignId,
        String campaignName,
        String brandName,
        LocalDate executionStartDate,
        LocalDate executionEndDate,
        Integer dailyTargetPlayCount,
        String description,
        CampaignCreativeType creativeType,
        String creativeUrl,
        Long mediaUnitId,
        String mediaName,
        String mediaLocationAddress,
        Integer mediaWidthMm,
        Integer mediaHeightMm,
        Integer mediaResolutionWidthPx,
        Integer mediaResolutionHeightPx,
        List<MediaUnitShapeType> mediaShapeTypes
) {
    public static TeamCampaignDetailResponse from(Campaign campaign, String creativeUrl) {
        MediaUnit mediaUnit = campaign.getMediaUnit();
        return new TeamCampaignDetailResponse(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getBrandName(),
                campaign.getExecutionStartDate(),
                campaign.getExecutionEndDate(),
                campaign.getDailyTargetPlayCount(),
                campaign.getDescription(),
                campaign.getCreativeType(),
                creativeUrl,
                mediaUnit.getId(),
                mediaUnit.getMediaName(),
                mediaUnit.getLocationAddress(),
                mediaUnit.getWidthMm(),
                mediaUnit.getHeightMm(),
                mediaUnit.getResolutionWidthPx(),
                mediaUnit.getResolutionHeightPx(),
                mediaUnit.getShapeTypes()
        );
    }
}
