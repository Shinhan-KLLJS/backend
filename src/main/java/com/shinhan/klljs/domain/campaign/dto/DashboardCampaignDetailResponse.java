package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;

/**
 * 캠페인 상세정보 조회(GET /api/v1/dashboard/campaigns/{campaign_id})의 응답 바디.
 * 스펙 3절 "Response Fields"에 정의된 필드 그대로다.
 */
public record DashboardCampaignDetailResponse(
        Long campaignId,
        String campaignName,
        String brandName,
        String description,
        String imageUrl,
        CampaignStatus status,
        Long mediaUnitId,
        Integer dailyTargetPlayCount,
        PeriodRange executionPeriod,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod,
        PeriodStatus periodStatus
) {
    public static DashboardCampaignDetailResponse from(Campaign campaign, CampaignPeriodContext periodContext) {
        return new DashboardCampaignDetailResponse(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getBrandName(),
                campaign.getDescription(),
                campaign.getImageUrl(),
                campaign.getStatus(),
                // Campaign.mediaUnit은 DB상 nullable FK지만, 캠페인 생성 화면에서 매체 선택이
                // 필수라 실제로는 항상 값이 있다고 가정한다(스펙에서도 mediaUnitId는 number 타입,
                // null 아님). 만약 이 가정이 깨지면(등록 플로우 버그 등) 여기서 NPE가 나므로,
                // 나중에 정말 media_unit_id가 없는 캠페인이 생길 수 있다는 게 확인되면
                // 이 매핑을 방어적으로 바꿔야 한다.
                campaign.getMediaUnit().getId(),
                campaign.getDailyTargetPlayCount(),
                periodContext.executionPeriod(),
                periodContext.selectedPeriod(),
                periodContext.effectivePeriod(),
                periodContext.periodStatus()
        );
    }
}
