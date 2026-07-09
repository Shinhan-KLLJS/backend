package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 5-2. 시간별 누적 그래프 API(GET .../realtime-graph/hourly)의 응답 바디. 스펙 5-2절 "Response Fields" 그대로다.
 *
 * 5-1(RealtimeGraphResponse)과 같은 화면 카드(노출/주목 흐름 그래프)에 쓰이지만 응답 모양은 다르다 -
 * 5-1은 "오늘만" 조회하는 커서 기반 폴링 API라 pollIntervalSec/lastEventTime/hasMore 같은 필드를 쓰고,
 * 5-2는 campaign_id + 기간을 받는 다른 대시보드 API들(깔대기, 평균 시청시간 등)과 같은
 * "selectedPeriod/effectivePeriod/periodStatus + aggregationUnit/aggregationCutoffTime/refreshIntervalSec" 틀을 공유한다.
 */
public record HourlyGraphResponse(
        Long campaignId,
        PeriodRange selectedPeriod,
        PeriodRange effectivePeriod, // BEFORE_EXECUTION이면 null
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        String aggregationUnit, // 항상 "HOUR"
        OffsetDateTime aggregationCutoffTime, // 집계에 포함된 데이터의 배타적 상한 시각. BEFORE_EXECUTION이면 null
        int refreshIntervalSec,
        List<Point> points
) {
    /** 1시간 단위 그래프 포인트 하나. eventTime은 해당 1시간 구간의 시작 시각(KST). */
    public record Point(
            OffsetDateTime eventTime,
            long exposedPopulationCount, // 해당 시간 동안의 ots_count 합계
            long attentionPopulationCount // 해당 시간 동안의 lts_count 합계
    ) {
    }
}
