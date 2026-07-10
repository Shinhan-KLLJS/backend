package com.shinhan.klljs.domain.vision.dto;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 5-1. 실시간 그래프 API(GET .../realtime-graph)의 응답 바디. 스펙 5-1절 "Response Fields" 그대로다.
 */
public record RealtimeGraphResponse(
        Long campaignId,
        LocalDate selectedDate,
        PeriodRange effectivePeriod,
        PeriodStatus periodStatus,
        OffsetDateTime serverTime,
        int pollIntervalSec,
        int overlapSec,
        OffsetDateTime lastEventTime,
        OffsetDateTime nextPollAfter,
        Long dataDelaySec,
        boolean hasMore,
        List<Point> points
) {
    public record Point(
            OffsetDateTime eventTime,
            BigDecimal intervalSec,
            int exposedPopulationCount,
            int attentionPopulationCount,
            String source
    ) {
    }
}
