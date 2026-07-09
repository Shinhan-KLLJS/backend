package com.shinhan.klljs.domain.campaign.util;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;

import java.time.LocalDate;

/**
 * 스펙 0절 "기간 처리 규칙"을 구현한 공용 유틸리티.
 * campaign_id + 기간(selected_start_date/selected_end_date)을 받는 모든 API
 * (상세조회, 송출정보, 깔대기, 평균시청시간, 성별연령비율, 시간연령노출도 등)가 공통으로 이 로직을 쓴다.
 * 그래서 KstDateTimes처럼 Spring 빈이 아니라 순수 static 유틸리티로 만들었다 - 상태가 없고
 * (Campaign, 날짜 두 개) -> 결과값 하나로 끝나는 순수 함수라 어디서든 그냥 호출해서 쓸 수 있다.
 */
public final class CampaignPeriodResolver {

    private CampaignPeriodResolver() {
    }

    /**
     * @throws GeneralException selectedStartDate가 selectedEndDate보다 늦으면 (요청 자체가 잘못됨) DASHBOARD_400_001
     */
    public static CampaignPeriodContext resolve(Campaign campaign, LocalDate selectedStartDate, LocalDate selectedEndDate) {
        if (selectedStartDate.isAfter(selectedEndDate)) {
            throw new GeneralException(CampaignErrorCode.INVALID_PERIOD);
        }

        LocalDate executionStartDate = campaign.getExecutionStartDate();
        LocalDate executionEndDate = campaign.getExecutionEndDate();

        PeriodRange executionPeriod = new PeriodRange(executionStartDate, executionEndDate);
        PeriodRange selectedPeriod = new PeriodRange(selectedStartDate, selectedEndDate);

        // 케이스 1) 선택 기간 전체가 집행 시작일보다 앞섬 -> 아직 집계할 데이터가 없다.
        // 예: 캠페인이 7/1~7/31인데 선택 기간이 6/1~6/30이면 완전히 집행 전.
        if (selectedEndDate.isBefore(executionStartDate)) {
            return new CampaignPeriodContext(executionPeriod, selectedPeriod, null, PeriodStatus.BEFORE_EXECUTION);
        }

        // 케이스 2) 선택 기간 전체가 집행 종료일보다 뒤임 -> 캠페인이 이미 끝났으므로,
        // 선택 기간과 상관없이 "집행 기간 전체"의 확정된 데이터를 그대로 돌려준다.
        // 예: 캠페인이 7/1~7/31인데 선택 기간이 8/1~8/7이면, effectivePeriod는 7/1~7/31 전체가 된다.
        if (selectedStartDate.isAfter(executionEndDate)) {
            return new CampaignPeriodContext(executionPeriod, selectedPeriod, executionPeriod, PeriodStatus.AFTER_EXECUTION);
        }

        // 케이스 3) 그 외 = 선택 기간과 집행 기간이 하루 이상 겹침 -> 겹치는 교집합만 집계 대상으로 삼는다.
        // 예: 캠페인 7/1~7/31, 선택 기간 6/30~7/2 -> 겹치는 구간은 7/1~7/2.
        // (선택 시작일과 집행 시작일 중 더 늦은 쪽, 선택 종료일과 집행 종료일 중 더 이른 쪽을 취하면 교집합이 나온다)
        LocalDate effectiveStartDate = selectedStartDate.isAfter(executionStartDate) ? selectedStartDate : executionStartDate;
        LocalDate effectiveEndDate = selectedEndDate.isBefore(executionEndDate) ? selectedEndDate : executionEndDate;
        PeriodRange effectivePeriod = new PeriodRange(effectiveStartDate, effectiveEndDate);

        return new CampaignPeriodContext(executionPeriod, selectedPeriod, effectivePeriod, PeriodStatus.IN_EXECUTION);
    }

    /** resolve()의 결과. 이 4개 필드는 대부분의 캠페인 조회 API 응답에 거의 그대로 들어간다. */
    public record CampaignPeriodContext(
            PeriodRange executionPeriod,
            PeriodRange selectedPeriod,
            PeriodRange effectivePeriod, // BEFORE_EXECUTION일 때만 null
            PeriodStatus periodStatus
    ) {
    }
}
