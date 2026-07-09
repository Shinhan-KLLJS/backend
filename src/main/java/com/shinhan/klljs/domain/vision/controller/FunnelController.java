package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.FunnelResponse;
import com.shinhan.klljs.domain.vision.service.FunnelService;
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
 * 6. 깔대기 그래프 대시보드 조회 API(스펙 6절). 전체 유동인구/노출인구/주목인구/주목 전환률과
 * 오늘 조회 시 어제 대비 증가율을 응답한다. 상세조회/송출정보와 쿼리 파라미터 제약이 동일하다
 * (selected_start_date/selected_end_date 둘 다 필수, "yyyy-MM-dd" 형식).
 */
@RestController
@RequiredArgsConstructor
public class FunnelController implements FunnelControllerDocs {

    private final FunnelService funnelService;

    @Override
    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/funnel")
    public ApiResponse<FunnelResponse> getFunnel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        FunnelResponse response = funnelService.getFunnel(userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
