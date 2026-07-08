package com.shinhan.klljs.domain.campaign.util;

import com.shinhan.klljs.domain.campaign.dto.PeriodRange;
import com.shinhan.klljs.domain.campaign.dto.PeriodStatus;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;

import java.time.LocalDate;

/**
 * 0절 "기간 처리 규칙"을 구현한 공용 유틸리티. 캠페인_id를 받는 모든 기간 조회 API가 공통으로 쓴다.
 */
public final class CampaignPeriodResolver {

    private CampaignPeriodResolver() {
    }

    public static CampaignPeriodContext resolve(Campaign campaign, LocalDate selectedStartDate, LocalDate selectedEndDate) {
        if (selectedStartDate.isAfter(selectedEndDate)) {
            throw new GeneralException(CampaignErrorCode.INVALID_PERIOD);
        }

        LocalDate executionStartDate = campaign.getExecutionStartDate();
        LocalDate executionEndDate = campaign.getExecutionEndDate();

        PeriodRange executionPeriod = new PeriodRange(executionStartDate, executionEndDate);
        PeriodRange selectedPeriod = new PeriodRange(selectedStartDate, selectedEndDate);

        if (selectedEndDate.isBefore(executionStartDate)) {
            return new CampaignPeriodContext(executionPeriod, selectedPeriod, null, PeriodStatus.BEFORE_EXECUTION);
        }

        if (selectedStartDate.isAfter(executionEndDate)) {
            return new CampaignPeriodContext(executionPeriod, selectedPeriod, executionPeriod, PeriodStatus.AFTER_EXECUTION);
        }

        LocalDate effectiveStartDate = selectedStartDate.isAfter(executionStartDate) ? selectedStartDate : executionStartDate;
        LocalDate effectiveEndDate = selectedEndDate.isBefore(executionEndDate) ? selectedEndDate : executionEndDate;
        PeriodRange effectivePeriod = new PeriodRange(effectiveStartDate, effectiveEndDate);

        return new CampaignPeriodContext(executionPeriod, selectedPeriod, effectivePeriod, PeriodStatus.IN_EXECUTION);
    }

    public record CampaignPeriodContext(
            PeriodRange executionPeriod,
            PeriodRange selectedPeriod,
            PeriodRange effectivePeriod,
            PeriodStatus periodStatus
    ) {
    }
}
