package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.vision.dto.AgeGroup;
import com.shinhan.klljs.domain.vision.dto.HourlyAgeExposureResponse;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository.OtsHourSums;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 7. 시간·연령별 노출도 API(스펙 7절)를 처리한다.
 *
 * OTS(노출인구) 성별·연령 14개 컬럼을 "하루 중 시간대"(날짜는 버리고 0~23시) 기준으로
 * 묶어서 히트맵 데이터를 만든다 - 여러 날짜에 걸친 같은 시간대(예: 이틀치의 14시)를
 * 하나로 합쳐서 "하루 중 언제가 붐비는지" 패턴을 보여주는 게 목적이다.
 *
 * DB 집계(VisionSummary5sRepository.sumOtsByHourOfDay)는 UTC 기준 시(hour)로 묶어서 주므로,
 * 이 클래스에서 KST 기준 시(hour-of-day)로 다시 라벨링한다 - UTC/KST는 9시간 고정 차이라
 * (hourOfDayUtc + 9) % 24로 1:1 변환되고 겹침/병합이 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class HourlyAgeExposureService {

    private static final String AGGREGATION_UNIT = "HOUR";
    private static final int REFRESH_INTERVAL_SEC = 3600;
    // 매체 운영 시작 시각(KST). DashboardCampaignDeliveryService.PLAY_START_TIME과 같은
    // "06:00부터 운영"이라는 가정을 공유한다 - hours[] 축의 시작점으로 쓴다.
    private static final int OPERATING_START_HOUR = 6;
    private static final int LAST_HOUR_OF_DAY = 23;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HourlyAgeExposureResponse getHourlyAgeExposure(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);

        // BEFORE_EXECUTION: 집행 전이라 집계할 데이터가 없다 (스펙 0절 기간 처리 규칙).
        // ageGroups는 화면에 표시할 고정 축이라 이 경우에도 채운다 (연령대 자체는 캠페인 상태와 무관).
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new HourlyAgeExposureResponse(
                    campaign.getId(), periodContext.selectedPeriod(), null, periodContext.periodStatus(),
                    serverTime, AGGREGATION_UNIT, null, REFRESH_INTERVAL_SEC,
                    Collections.emptyList(), fixedAgeGroups(), Collections.emptyList()
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        LocalDate today = KstDateTimes.todayKst(nowUtc);
        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.HOURS);

        List<OtsHourSums> hourSumsUtc = visionSummary5sRepository.sumOtsByHourOfDay(campaign.getId(), window.fromUtc(), window.toUtc());
        Map<Integer, OtsHourSums> byKstHour = toKstHourMap(hourSumsUtc);

        List<String> hours = buildHoursAxis(effectivePeriod, today, nowUtc);
        List<HourlyAgeExposureResponse.Cell> cells = buildCells(byKstHour);

        return new HourlyAgeExposureResponse(
                campaign.getId(), periodContext.selectedPeriod(), effectivePeriod, periodContext.periodStatus(),
                serverTime, AGGREGATION_UNIT, window.cutoffTime(), REFRESH_INTERVAL_SEC,
                hours, fixedAgeGroups(), cells
        );
    }

    /** UTC 기준 시(hour-of-day)를 KST 기준 시로 재라벨링한다. 9시간 고정 차이라 1:1 변환, 병합 불필요. */
    private Map<Integer, OtsHourSums> toKstHourMap(List<OtsHourSums> hourSumsUtc) {
        Map<Integer, OtsHourSums> byKstHour = new HashMap<>();
        for (OtsHourSums row : hourSumsUtc) {
            int kstHour = (row.hourOfDayUtc() + 9) % 24;
            byKstHour.put(kstHour, row);
        }
        return byKstHour;
    }

    /**
     * effectivePeriod가 완전히 과거면 하루 전체(06~23시)를, 오늘이 포함되면 운영 시작 시각부터
     * "지금" KST 시(hour)까지를 축으로 보여준다 - 아직 데이터가 없는 진행 중인 시간대도
     * 축에는 표시해서(빈 칸으로), 하루가 지나면서 축이 갑자기 늘어나 보이지 않게 한다.
     */
    private List<String> buildHoursAxis(PeriodRange effectivePeriod, LocalDate today, LocalDateTime nowUtc) {
        int endHourInclusive = effectivePeriod.endDate().isBefore(today)
                ? LAST_HOUR_OF_DAY
                : KstDateTimes.toKst(nowUtc).getHour();

        List<String> hours = new ArrayList<>();
        for (int hour = OPERATING_START_HOUR; hour <= endHourInclusive; hour++) {
            hours.add(formatHour(hour));
        }
        return hours;
    }

    private List<HourlyAgeExposureResponse.AgeGroupLabel> fixedAgeGroups() {
        return Arrays.stream(AgeGroup.values())
                .map(ageGroup -> new HourlyAgeExposureResponse.AgeGroupLabel(ageGroup, ageGroup.getLabel()))
                .toList();
    }

    /**
     * (KST 시, 연령대) 조합 중 노출이 하나라도 있는 것만 셀로 만든다(0인 조합은 생략 - 5-2/6절과
     * 같은 "빈 데이터는 채워 넣지 않는다" 방식). intensityLevel은 이 응답에 포함된 셀들
     * 중에서의 최댓값 기준으로 계산해야 해서, 원시 합계를 다 모은 뒤 2단계로 계산한다.
     */
    private List<HourlyAgeExposureResponse.Cell> buildCells(Map<Integer, OtsHourSums> byKstHour) {
        List<RawCell> rawCells = new ArrayList<>();
        for (Map.Entry<Integer, OtsHourSums> entry : byKstHour.entrySet()) {
            int kstHour = entry.getKey();
            OtsHourSums sums = entry.getValue();
            for (AgeGroup ageGroup : AgeGroup.values()) {
                long male = sums.male(ageGroup);
                long female = sums.female(ageGroup);
                long total = male + female;
                if (total > 0) {
                    rawCells.add(new RawCell(kstHour, ageGroup, total, male, female));
                }
            }
        }

        long maxExposure = rawCells.stream().mapToLong(RawCell::exposureCount).max().orElse(0);
        long maxMaleExposure = rawCells.stream().mapToLong(RawCell::maleExposureCount).max().orElse(0);
        long maxFemaleExposure = rawCells.stream().mapToLong(RawCell::femaleExposureCount).max().orElse(0);

        return rawCells.stream()
                .sorted(Comparator.<RawCell>comparingInt(c -> c.kstHour).thenComparingInt(c -> c.ageGroup.ordinal()))
                .map(c -> new HourlyAgeExposureResponse.Cell(
                        formatHour(c.kstHour), c.ageGroup,
                        c.exposureCount, intensityLevel(c.exposureCount, maxExposure),
                        c.maleExposureCount, intensityLevel(c.maleExposureCount, maxMaleExposure),
                        c.femaleExposureCount, intensityLevel(c.femaleExposureCount, maxFemaleExposure)
                ))
                .toList();
    }

    /** intensityLevel = exposureCount == 0 ? 0 : min(4, ceil(exposureCount / max * 4)) (스펙 7절 계산식 그대로). */
    private int intensityLevel(long value, long max) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.min(4, Math.ceil(value * 4.0 / max));
    }

    private String formatHour(int hour) {
        return String.format("%02d", hour);
    }

    private record RawCell(int kstHour, AgeGroup ageGroup, long exposureCount, long maleExposureCount, long femaleExposureCount) {
    }
}
