package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.AggregationWindow;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.vision.dto.AgeGroup;
import com.shinhan.klljs.domain.vision.dto.DemographicViewRatioResponse;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository.DemographicSums;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 7. 성별·연령 시청 비율 API(스펙 7절)를 처리한다.
 *
 * LTS(주목인구) 성별·연령 14개 컬럼을 집계해서, 전체 시청자 대비 비율(maleRatio/femaleRatio/
 * totalRatio)과 같은 성별 안에서의 비율(maleShareRatio/femaleShareRatio)을 함께 계산한다.
 * 두 비율 모두 분모를 lts_male_count/lts_female_count가 아니라 "7개 연령 컬럼의 합"으로
 * 통일해서 쓴다 - 얼굴 인식은 됐는데 나이 추정만 실패한 케이스가 있으면 count 컬럼이 연령
 * 컬럼 합보다 클 수 있어서, 연령 컬럼 합을 쓰면 이 문제와 무관하게 비율의 합이 항상 정확히
 * 100%가 되기 때문이다 (스펙 7절 "분모는 ... 연령 컬럼의 합을 쓴다" 문단 참고).
 */
@Service
@RequiredArgsConstructor
public class DemographicViewRatioService {

    private static final String AGGREGATION_UNIT = "HOUR";
    private static final int REFRESH_INTERVAL_SEC = 3600;

    private final DashboardCampaignQueryService dashboardCampaignQueryService;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DemographicViewRatioResponse getDemographicViewRatio(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = dashboardCampaignQueryService.getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        OffsetDateTime serverTime = KstDateTimes.toKstOffset(nowUtc);

        // BEFORE_EXECUTION: 집행 전이라 집계할 데이터가 없다 (스펙 0절 기간 처리 규칙).
        if (periodContext.periodStatus() == PeriodStatus.BEFORE_EXECUTION) {
            return new DemographicViewRatioResponse(
                    campaign.getId(), periodContext.selectedPeriod(), null, periodContext.periodStatus(),
                    serverTime, AGGREGATION_UNIT, null, REFRESH_INTERVAL_SEC,
                    new DemographicViewRatioResponse.GenderSummary(null, null), Collections.emptyList()
            );
        }

        PeriodRange effectivePeriod = periodContext.effectivePeriod();
        LocalDate today = KstDateTimes.todayKst(nowUtc);
        AggregationWindow window = CampaignPeriodResolver.resolveAggregationWindow(effectivePeriod, today, nowUtc, ChronoUnit.HOURS);

        DemographicSums sums = visionSummary5sRepository.sumDemographicsInRange(campaign.getId(), window.fromUtc(), window.toUtc());

        long maleAgeTotal = sums.maleTotal();
        long femaleAgeTotal = sums.femaleTotal();
        long totalAgeTotal = maleAgeTotal + femaleAgeTotal;

        // genderSummary는 "number/null" 타입이라(스펙), 집계 대상이 아예 없으면(totalAgeTotal==0) null로 응답한다.
        Double genderMaleRatio = totalAgeTotal == 0 ? null : maleAgeTotal * 100.0 / totalAgeTotal;
        Double genderFemaleRatio = totalAgeTotal == 0 ? null : femaleAgeTotal * 100.0 / totalAgeTotal;

        List<DemographicViewRatioResponse.AgeGroupRatio> ageGroups = Arrays.stream(AgeGroup.values())
                .map(ageGroup -> buildAgeGroupRatio(ageGroup, sums, totalAgeTotal, maleAgeTotal, femaleAgeTotal))
                .toList();

        return new DemographicViewRatioResponse(
                campaign.getId(), periodContext.selectedPeriod(), effectivePeriod, periodContext.periodStatus(),
                serverTime, AGGREGATION_UNIT, window.cutoffTime(), REFRESH_INTERVAL_SEC,
                new DemographicViewRatioResponse.GenderSummary(genderMaleRatio, genderFemaleRatio), ageGroups
        );
    }

    /**
     * maleRatio/femaleRatio/totalRatio: 분모가 totalAgeTotal(남녀 전체)이라 7개 연령대를
     * 다 더하면 genderSummary와 100%에 정확히 맞는다 (스펙 "number" 타입이라 null 대신 0으로 응답).
     * maleShareRatio/femaleShareRatio: 분모가 그 성별 안의 연령 합이라, 남/여 각각 7개를
     * 더하면 100%가 된다. 분모가 0이면 계산 불가하므로 0으로 응답한다(스펙 "분모가 0이면 전부 0" 규칙).
     */
    private DemographicViewRatioResponse.AgeGroupRatio buildAgeGroupRatio(
            AgeGroup ageGroup, DemographicSums sums, long totalAgeTotal, long maleAgeTotal, long femaleAgeTotal
    ) {
        long male = sums.male(ageGroup);
        long female = sums.female(ageGroup);

        double maleRatio = totalAgeTotal == 0 ? 0.0 : male * 100.0 / totalAgeTotal;
        double femaleRatio = totalAgeTotal == 0 ? 0.0 : female * 100.0 / totalAgeTotal;
        double totalRatio = maleRatio + femaleRatio;
        double maleShareRatio = maleAgeTotal == 0 ? 0.0 : male * 100.0 / maleAgeTotal;
        double femaleShareRatio = femaleAgeTotal == 0 ? 0.0 : female * 100.0 / femaleAgeTotal;

        return new DemographicViewRatioResponse.AgeGroupRatio(
                ageGroup, ageGroup.getLabel(), maleRatio, femaleRatio, totalRatio, maleShareRatio, femaleShareRatio
        );
    }
}
