package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.RealtimeGraphResponse;
import com.shinhan.klljs.domain.vision.service.RealtimeGraphService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 5-1. 실시간 그래프 API(스펙 5-1절). 날짜 파라미터가 없다 - 항상 서버 기준 오늘(KST)만 조회한다.
 * 과거/여러 날짜 조회는 5-2 API(추후 구현)가 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class RealtimeGraphController {

    private final RealtimeGraphService realtimeGraphService;

    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/realtime-graph")
    public ApiResponse<RealtimeGraphResponse> getRealtimeGraph(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam(value = "after_event_time", required = false) OffsetDateTime afterEventTime,
            @RequestParam(required = false) Integer limit
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        RealtimeGraphResponse response = realtimeGraphService.getRealtimeGraph(userId, campaignId, afterEventTime, limit);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
