package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.media.entity.MediaUnit;

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
        String mediaPhotoUrl,
        Integer dailyTargetPlayCount,
        PeriodRange executionPeriod,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod,
        PeriodStatus periodStatus
) {
    /**
     * imageUrl은 저장된 값이 아니라 creativeStorageKey + publicBaseUrl로 그때그때 계산한다
     * (campaign-page-api-spec.md의 creativeUrl과 동일한 소스·동일한 방식) - 예전엔 Campaign에
     * 별도로 저장하는 image_url 컬럼이 있었는데, 실제 등록 플로우가 이 컬럼을 채우는 걸 빠뜨려서
     * 항상 null이었다(로컬 목업 데이터만 직접 채워서 로컬에서는 안 드러났다). 저장 값에 의존하는
     * 대신 항상 같은 소스에서 계산하도록 바꿔 이 클래스의 값이 다시 어긋날 일이 없게 한다.
     */
    public static DashboardCampaignDetailResponse from(
            Campaign campaign, CampaignPeriodContext periodContext, String creativePublicBaseUrl
    ) {
        // Campaign.mediaUnit은 DB상 nullable FK지만, 캠페인 생성 화면에서 매체 선택이
        // 필수라 실제로는 항상 값이 있다고 가정한다(스펙에서도 mediaUnitId는 number 타입,
        // null 아님). 만약 이 가정이 깨지면(등록 플로우 버그 등) 여기서 NPE가 나므로,
        // 나중에 정말 media_unit_id가 없는 캠페인이 생길 수 있다는 게 확인되면
        // 이 매핑을 방어적으로 바꿔야 한다.
        MediaUnit mediaUnit = campaign.getMediaUnit();
        return new DashboardCampaignDetailResponse(
                campaign.getId(),
                campaign.getCampaignName(),
                campaign.getBrandName(),
                campaign.getDescription(),
                creativePublicBaseUrl + "/" + campaign.getCreativeStorageKey(),
                campaign.getStatus(),
                mediaUnit.getId(),
                mediaUnit.getPhotoUrl(),
                campaign.getDailyTargetPlayCount(),
                periodContext.executionPeriod(),
                periodContext.selectedPeriod(),
                periodContext.effectivePeriod(),
                periodContext.periodStatus()
        );
    }
}
