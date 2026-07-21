package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.vision.dto.HourlyGraphResponse;
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 5-2. 시간별 누적 그래프 API(스펙 5-2절)를 처리한다.
 *
 * 5-1(RealtimeGraphService)은 항상 "서버 기준 오늘"만 커서 방식으로 폴링하지만, 이 API는
 * selected_start_date~selected_end_date로 받은 임의의(하루~여러 날) 기간을 매번 통째로
 * 다시 조회해서 1시간 단위로 합산해 응답한다. 과거 기간이면 재조회해도 값이 바뀌지 않고,
 * 조회 기간에 오늘이 포함되면 아직 다 끝나지 않은 "진행 중인 시간대"만 제외하고 집계한다.
 */
@Service
@RequiredArgsConstructor
public class HourlyGraphService {

    private static final String AGGREGATION_UNIT = "HOUR";
    private static final int REFRESH_INTERVAL_SEC = 3600;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HourlyGraphResponse getHourlyGraph(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);

        // BEFORE_EXECUTION: 아직 집행 전이라 집계할 데이터 자체가 없다 (스펙 0절 기간 처리 규칙).
        // aggregationUnit/refreshIntervalSec는 다른 API들과 동일하게 값을 채우고,
        // effectivePeriod/aggregationCutoffTime만 null, points는 빈 배열로 응답한다.
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new HourlyGraphResponse(
                    campaign.getId(), periodContext.selectedPeriod(), null, periodContext.periodStatus(),
                    serverTime, AGGREGATION_UNIT, null, REFRESH_INTERVAL_SEC, Collections.emptyList()
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        LocalDate today = KstDateTimes.todayKst(nowUtc);

        // 조회 범위/커서 계산은 5-2/6/7절이 전부 공유하는 규칙이라 CampaignPeriodResolver로 뺐다
        // (오늘이 포함되면 "지금 진행 중인 시간대"는 제외, 완전히 과거면 구간 전체 포함).
        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.HOURS);

        List<VisionSummary5s> rows = visionSummary5sRepository.findAllInRange(campaign.getId(), window.fromUtc(), window.toUtc());
        List<HourlyGraphResponse.Point> points = aggregateByHour(rows, effectivePeriod.startDate());

        return new HourlyGraphResponse(
                campaign.getId(), periodContext.selectedPeriod(), effectivePeriod, periodContext.periodStatus(),
                serverTime, AGGREGATION_UNIT, window.cutoffTime(), REFRESH_INTERVAL_SEC, points
        );
    }

    /**
     * 5초 row들을 KST 기준 "시각(0~23시)"으로만 묶어 ots_count/lts_count를 합산한다 - 날짜는
     * 버리고 시간대만 키로 쓰므로, 선택 기간이 여러 날짜에 걸쳐 있으면 같은 시각(예: 06시)의
     * 여러 날짜치 데이터가 하나의 포인트로 합산된다(스펙 5-2절, 여러 날짜 조회 시 날짜별로
     * 나뉘지 않고 시각별로 누적).
     *
     * 응답의 eventTime은 조회 결과가 실제로 여러 날짜에 걸쳐 있어도 anchorDateKst(=effectivePeriod
     * 시작일) + 시각으로 만든 단일 날짜의 타임스탬프로 내려간다 - 프론트가 그래프 x축에 "시각"만
     * 쓰는 걸 전제로, 날짜 부분은 고정 앵커일 뿐 실제 그 날짜의 데이터만이 아니라는 점에 유의한다.
     *
     * 데이터가 아예 없는 시간대는 버킷 자체가 생기지 않아 points에서 통째로 빠진다 - 5-1 API가
     * 값이 없는 5초 구간을 0으로 채워 넣지 않는 것과 같은 방식이라 두 API의 동작이 일관된다.
     *
     * TreeMap을 쓰는 이유: 키가 0~23 시각이라, 여러 날짜의 row가 뒤섞여 들어와도 항상 시각
     * 오름차순으로 출력되도록 보장한다.
     */
    private List<HourlyGraphResponse.Point> aggregateByHour(List<VisionSummary5s> rows, LocalDate anchorDateKst) {
        Map<Integer, long[]> sumsByHourOfDayKst = new TreeMap<>();

        for (VisionSummary5s row : rows) {
            int hourOfDayKst = KstDateTimes.toKst(row.getEventTime()).getHour();
            long[] sums = sumsByHourOfDayKst.computeIfAbsent(hourOfDayKst, key -> new long[2]);
            sums[0] += row.getOtsCount();
            sums[1] += row.getLtsCount();
        }

        return sumsByHourOfDayKst.entrySet().stream()
                .map(entry -> new HourlyGraphResponse.Point(
                        anchorDateKst.atTime(entry.getKey(), 0).atOffset(KstDateTimes.KST),
                        entry.getValue()[0],
                        entry.getValue()[1]
                ))
                .toList();
    }
}
