package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDetailResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignListResponse;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
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

@RestController
@RequiredArgsConstructor
public class DashboardCampaignController {

    private final DashboardCampaignQueryService dashboardCampaignQueryService;

    @GetMapping("/api/v1/dashboard/campaigns")
    public ApiResponse<DashboardCampaignListResponse> getCampaigns(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CampaignStatus status
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        DashboardCampaignListResponse response = dashboardCampaignQueryService.getCampaigns(userId, keyword, status);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}")
    public ApiResponse<DashboardCampaignDetailResponse> getCampaignDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        DashboardCampaignDetailResponse response = dashboardCampaignQueryService.getCampaignDetail(
                userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
