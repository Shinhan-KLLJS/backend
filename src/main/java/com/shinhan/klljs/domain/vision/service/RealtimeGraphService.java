package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.vision.dto.RealtimeGraphResponse;
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * 5-1. 실시간 그래프 API(스펙 5-1절)를 처리한다. 이 API는 항상 서버 기준 오늘(KST)만 조회하고
 * (selected_date 파라미터 자체가 없다 - 컨트롤러 문서 참고), 과거/미래 날짜는 다루지 않는다.
 * 과거 데이터는 5-2 API(DashboardVisionTrendService, 아직 미구현)가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class RealtimeGraphService {

    private static final int POLL_INTERVAL_SEC = 5;
    private static final int DEFAULT_LIMIT = 60;
    private static final int MAX_LIMIT = 720;
    // SQS 메시지가 늦게 도착하거나 순서가 바뀌는 걸 감안해 커서보다 조금 더 이전 데이터까지
    // 겹쳐서 조회한다 (스펙 5-1절 "다만 SQS 메시지는 늦게 도착하거나..." 문단, 권장값 10~30초 중간값).
    private static final int OVERLAP_SEC = 15;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RealtimeGraphResponse getRealtimeGraph(Long userId, Long campaignId, OffsetDateTime afterEventTime, Integer limitParam) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        LocalDate today = KstDateTimes.todayKst(nowUtc);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);
        int limit = clampLimit(limitParam);

        PeriodStatus periodStatus = resolveTodayPeriodStatus(campaign, today);

        // 오늘이 캠페인 집행 기간 밖이면(집행 전/후) 라이브 데이터가 없다 - 빈 배열로 응답한다.
        // 과거 실적은 5-2 API를 쓰라고 안내하는 게 이 API의 역할이 아니라 프론트가 이미
        // 호출 시점에 5-1/5-2를 나눠 부르므로, 여기서는 그냥 빈 데이터만 주면 된다.
        if (periodStatus != PeriodStatus.IN_EXECUTION) {
            return new RealtimeGraphResponse(
                    campaign.getId(), today, null, periodStatus, serverTime,
                    POLL_INTERVAL_SEC, 0, null, serverTime.plusSeconds(POLL_INTERVAL_SEC), null, false,
                    Collections.emptyList()
            );
        }

        KstDateTimes.UtcRange todayRangeUtc = KstDateTimes.kstDayRangeUtc(today);

        int overlapSec = afterEventTime == null ? 0 : OVERLAP_SEC;
        LocalDateTime cursorUtc = afterEventTime == null
                ? null
                : afterEventTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().minusSeconds(OVERLAP_SEC);

        // limit + 1개를 가져와서 하나가 더 있으면 hasMore=true로 판단하고(흔한 페이지네이션 트릭),
        // 실제 응답에는 limit개까지만 담는다.
        List<VisionSummary5s> rowsDesc = visionSummary5sRepository.findRecentPoints(
                campaign.getId(), todayRangeUtc.startUtc(), todayRangeUtc.endUtc(), cursorUtc, PageRequest.of(0, limit + 1));

        boolean hasMore = rowsDesc.size() > limit;
        List<VisionSummary5s> trimmed = hasMore ? rowsDesc.subList(0, limit) : rowsDesc;

        // DESC로 가져왔으니(최근 N개를 정확히 뽑기 위해) 응답 직전에 시간순(ASC)으로 뒤집는다.
        List<RealtimeGraphResponse.Point> points = trimmed.reversed().stream()
                .map(this::toPoint)
                .toList();

        OffsetDateTime lastEventTime = points.isEmpty() ? null : points.get(points.size() - 1).eventTime();
        Long dataDelaySec = lastEventTime == null ? null : ChronoUnit.SECONDS.between(lastEventTime, serverTime);

        return new RealtimeGraphResponse(
                campaign.getId(), today, new PeriodRange(today, today), periodStatus, serverTime,
                POLL_INTERVAL_SEC, overlapSec, lastEventTime, serverTime.plusSeconds(POLL_INTERVAL_SEC), dataDelaySec, hasMore,
                points
        );
    }

    private PeriodStatus resolveTodayPeriodStatus(Campaign campaign, LocalDate today) {
        if (today.isBefore(campaign.getExecutionStartDate())) {
            return PeriodStatus.BEFORE_EXECUTION;
        }
        if (today.isAfter(campaign.getExecutionEndDate())) {
            return PeriodStatus.AFTER_EXECUTION;
        }
        return PeriodStatus.IN_EXECUTION;
    }

    private int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limitParam, MAX_LIMIT));
    }

    private RealtimeGraphResponse.Point toPoint(VisionSummary5s row) {
        return new RealtimeGraphResponse.Point(
                KstDateTimes.toKstOffset(row.getEventTime()),
                row.getIntervalSec(),
                row.getOtsCount(),
                row.getLtsCount(),
                "DB"
        );
    }
}
