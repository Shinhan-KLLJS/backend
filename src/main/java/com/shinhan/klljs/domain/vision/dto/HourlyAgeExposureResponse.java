package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 7. 시간·연령별 노출도 API(GET .../hourly-age-exposure)의 응답 바디. 스펙 7절 "Response Fields" 그대로다.
 *
 * 화면의 전체/남성/여성 토글은 API를 다시 호출하지 않는다 - cells[] 안에 세 뷰의
 * exposureCount/intensityLevel 쌍을 전부 담아 한 번에 내려준다.
 */
public record HourlyAgeExposureResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod, // BEFORE_EXECUTION이면 null
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        String aggregationUnit, // 항상 "HOUR"
        OffsetDateTime aggregationCutoffTime, // 집계에 포함된 데이터의 배타적 상한 시각. BEFORE_EXECUTION이면 null
        int refreshIntervalSec, // 항상 3600
        List<String> hours, // 화면에 표시할 시간 축("HH"). 토글과 무관하게 공통
        List<AgeGroupLabel> ageGroups, // 화면에 표시할 연령 축. 토글과 무관하게 공통, 항상 7개 고정
        List<Cell> cells // 데이터가 있는 (시간, 연령대) 조합만 포함 (0인 조합은 생략)
) {
    public record AgeGroupLabel(AgeGroup ageGroup, String label) {
    }

    /**
     * 히트맵 셀 하나. intensityLevel 3종은 서로 독립적으로 정규화된다 - maleIntensityLevel은
     * 이 응답의 maleExposureCount 최댓값 기준이라, 전체 탭 최댓값(exposureCount 기준)과 무관하다
     * (스펙 7절 "intensityLevel 계산" 문단 참고 - 남녀 각 뷰에서 "상대적으로 어디가 진한지"를
     * 보여주는 게 목적이라 각자 따로 정규화한다).
     */
    public record Cell(
            String hour, // "HH" 형식(KST)
            AgeGroup ageGroup,
            long exposureCount, // 전체(남녀 합산) 노출 수. 전체 탭용
            int intensityLevel, // 전체 탭 색상 강도. 1(Less)~4(More), 데이터 없으면 0
            long maleExposureCount,
            int maleIntensityLevel,
            long femaleExposureCount,
            int femaleIntensityLevel
    ) {
    }
}
