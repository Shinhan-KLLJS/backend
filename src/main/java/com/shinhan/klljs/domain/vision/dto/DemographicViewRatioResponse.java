package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 7. 성별·연령 시청 비율 API(GET .../demographic-view-ratio)의 응답 바디. 스펙 7절 "Response Fields" 그대로다.
 *
 * 화면의 전체/남성/여성 토글은 API를 다시 호출하지 않는다 - ageGroups[] 안에 세 뷰에
 * 필요한 값(maleRatio/femaleRatio/totalRatio는 전체 탭용, maleShareRatio는 남성 탭용,
 * femaleShareRatio는 여성 탭용)을 전부 담아 한 번에 내려준다.
 */
public record DemographicViewRatioResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod, // BEFORE_EXECUTION이면 null
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        String aggregationUnit, // 항상 "HOUR"
        OffsetDateTime aggregationCutoffTime, // 집계에 포함된 데이터의 배타적 상한 시각. BEFORE_EXECUTION이면 null
        int refreshIntervalSec, // 항상 3600
        GenderSummary genderSummary,
        List<AgeGroupRatio> ageGroups
) {
    /** 우측 상단 범례용 성별 비율. 토글 상태와 무관하게 항상 하나의 값. 집계 대상이 0명이면 null. */
    public record GenderSummary(Double maleRatio, Double femaleRatio) {
    }

    /**
     * 연령대 하나의 비율 정보. maleRatio/femaleRatio/totalRatio는 "전체 시청자" 대비 비율이라
     * 7개 연령대를 다 더하면 각각 genderSummary.maleRatio/femaleRatio, 100%가 된다.
     * maleShareRatio/femaleShareRatio는 "그 성별 안에서"의 비율이라 분모가 다르다.
     */
    public record AgeGroupRatio(
            AgeGroup ageGroup,
            String label,
            double maleRatio,
            double femaleRatio,
            double totalRatio,
            double maleShareRatio,
            double femaleShareRatio
    ) {
    }
}
