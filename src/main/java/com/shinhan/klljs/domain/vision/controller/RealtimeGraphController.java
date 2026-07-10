package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.HourlyGraphResponse;
import com.shinhan.klljs.domain.vision.dto.RealtimeGraphResponse;
import com.shinhan.klljs.domain.vision.service.HourlyGraphService;
import com.shinhan.klljs.domain.vision.service.RealtimeGraphService;
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
import java.time.OffsetDateTime;

/**
 * 노출/주목 흐름 그래프 API(스펙 5절). 같은 화면 카드를 두 개의 엔드포인트가 나눠 담당한다:
 * - 5-1(/realtime-graph): 날짜 파라미터가 없다 - 항상 서버 기준 오늘(KST)만 커서 방식으로 조회.
 * - 5-2(/realtime-graph/hourly): selected_start_date~selected_end_date로 받은 과거/여러 날짜
 *   기간을 1시간 단위로 합산해서 조회. 프론트는 스펙 5절 조건에 따라 둘 중 하나만 호출한다.
 */
@RestController
@RequiredArgsConstructor
public class RealtimeGraphController implements RealtimeGraphControllerDocs {

    private final RealtimeGraphService realtimeGraphService;
    private final HourlyGraphService hourlyGraphService;

    @Override
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

    /**
     * 5-2. 시간별 누적 그래프. 상세조회/송출정보와 쿼리 파라미터 제약이 동일하다
     * (selected_start_date/selected_end_date 둘 다 필수, "yyyy-MM-dd" 형식).
     * Swagger 설명은 RealtimeGraphControllerDocs에 몰아뒀다.
     */
    @Override
    @GetMapping("/api/v1/dashboard/campaigns/{campaignId}/realtime-graph/hourly")
    public ApiResponse<HourlyGraphResponse> getHourlyGraph(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long campaignId,
            @RequestParam("selected_start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @RequestParam("selected_end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        HourlyGraphResponse response = hourlyGraphService.getHourlyGraph(userId, campaignId, selectedStartDate, selectedEndDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
