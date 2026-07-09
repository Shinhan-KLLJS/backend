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

/**
 * 홈 대시보드의 캠페인 목록/상세 조회 API. 스펙 문서(docs/home-dashboard-api-spec.md) 2절, 3절.
 *
 * 두 엔드포인트 모두 SecurityConfig의 anyRequest().authenticated() 규칙으로 보호되므로
 * (permitAll 목록에 없음) JWT 없이 호출하면 이 컨트롤러에 들어오기 전에 401로 막힌다.
 * @AuthenticationPrincipal Jwt jwt로 이미 서명 검증이 끝난 JWT를 그대로 받고,
 * jwt.getSubject()가 로그인 시 발급한 내부 사용자 ID(문자열)라서 Long으로 파싱해서 쓴다.
 */
@RestController
@RequiredArgsConstructor
public class DashboardCampaignController {

    private final DashboardCampaignQueryService dashboardCampaignQueryService;

    /**
     * 캠페인 목록 조회. keyword/status 둘 다 선택값이라 없으면 전체 목록을 준다.
     */
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

    /**
     * 캠페인 상세정보 조회. 기간 파라미터 둘 다 필수(스펙 3절 Query Parameters).
     * 쿼리 파라미터명이 스펙 규칙(0절)대로 snake_case(selected_start_date)라서
     * 자바 변수명(camelCase)과 다르므로 @RequestParam에 명시적으로 이름을 지정한다.
     * @DateTimeFormat으로 "yyyy-MM-dd" 형식을 명시해서, 스펙에 정의된 날짜 형식과
     * 다른 문자열이 오면 Spring이 바인딩 단계에서 바로 400을 내도록 한다.
     */
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
