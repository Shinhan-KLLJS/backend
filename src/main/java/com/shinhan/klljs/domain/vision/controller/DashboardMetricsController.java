package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.AverageWatchTimeResponse;
import com.shinhan.klljs.domain.vision.dto.DemographicViewRatioResponse;
import com.shinhan.klljs.domain.vision.dto.HourlyAgeExposureResponse;
import com.shinhan.klljs.domain.vision.service.AverageWatchTimeService;
import com.shinhan.klljs.domain.vision.service.DemographicViewRatioService;
import com.shinhan.klljs.domain.vision.service.HourlyAgeExposureService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 7. 평균 시청시간 및 1시간 단위 대시보드 API(스펙 7절). 평균 시청시간/성별·연령 시청 비율/
 * 시간·연령별 노출도 3개 API를 한 컨트롤러에 묶었다 - 세 API가 같은 문서 절에서 공통 Request/
 * Response 틀(campaignId + selectedPeriod/effectivePeriod/periodStatus + aggregationUnit/
 * aggregationCutoffTime/refreshIntervalSec)을 공유하기 때문이다. 쿼리 파라미터 제약은
 * 상세조회/송출정보와 동일하다(selected_start_date/selected_end_date 둘 다 필수, "yyyy-MM-dd" 형식).
 */
@RestController
@RequiredArgsConstructor
public class DashboardMetricsController implements DashboardMetricsControllerDocs {

    private final AverageWatchTimeService averageWatchTimeService;
    private final DemographicViewRatioService demographicViewRatioService;
    private final HourlyAgeExposureService hourlyAgeExposureService;

    @Override
    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/average-watch-time")
    public ApiResponse<AverageWatchTimeResponse> getAverageWatchTime(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        AverageWatchTimeResponse response = averageWatchTimeService.getAverageWatchTime(
                userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/demographic-view-ratio")
    public ApiResponse<DemographicViewRatioResponse> getDemographicViewRatio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        DemographicViewRatioResponse response = demographicViewRatioService.getDemographicViewRatio(
                userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/hourly-age-exposure")
    public ApiResponse<HourlyAgeExposureResponse> getHourlyAgeExposure(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        HourlyAgeExposureResponse response = hourlyAgeExposureService.getHourlyAgeExposure(
                userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
