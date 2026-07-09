package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 7. 평균 시청시간 API(GET .../average-watch-time)의 응답 바디. 스펙 7절 "Response Fields" 그대로다.
 */
public record AverageWatchTimeResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod, // BEFORE_EXECUTION이면 null
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        String aggregationUnit, // 항상 "MINUTE"
        OffsetDateTime aggregationCutoffTime, // 집계에 포함된 데이터의 배타적 상한 시각. BEFORE_EXECUTION이면 null
        int refreshIntervalSec, // 항상 60
        Double averageWatchTimeSec, // 집행 전이거나 주목인구가 0이면 null
        List<WatchTimeBucket> watchTimeBuckets
) {
    /** 시청시간 구간별 분포 하나. 4개 구간(1~2초, 2~3초, 3~4초, 4초 이상)의 count/ratio 합은 각각 전체/100%. */
    public record WatchTimeBucket(
            String bucket, // "1_TO_2S", "2_TO_3S", "3_TO_4S", "OVER_4S"
            String label,
            long count,
            double ratio // 전체 구간 합 대비 비율(%). 4개 구간 데이터가 전부 0이면 0
    ) {
    }
}
