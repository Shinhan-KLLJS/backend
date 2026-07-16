package com.shinhan.klljs.domain.traffic.controller;

import com.shinhan.klljs.domain.traffic.dto.PopulationIngestResponse;
import com.shinhan.klljs.domain.traffic.service.PopulationIngestService;
import com.shinhan.klljs.domain.traffic.util.PopulationDataLag;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 서울시 250m격자 생활인구(내국인) 수동 적재 API. MVP 운영자가 Swagger에서 인증 없이 트리거한다
 * (MediaUnitController의 관리자 API와 동일한 패턴 - SecurityConfig에 permitAll로 등록되어 있다).
 * 최종적으로는 스케줄러가 매일 이 로직을 호출하도록 얹을 예정이다.
 */
@RestController
@RequiredArgsConstructor
public class PopulationIngestController implements PopulationIngestControllerDocs {

    private final PopulationIngestService populationIngestService;
    private final Clock clock;

    @Override
    @PostMapping("/api/v1/admin/traffic/population-ingest")
    public ApiResponse<PopulationIngestResponse> ingest(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {
        LocalDate populationDate = date != null
                ? date
                : KstDateTimes.todayKst(LocalDateTime.now(clock)).minusDays(PopulationDataLag.DAYS);

        int gridCount = populationIngestService.ingest(populationDate);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new PopulationIngestResponse(populationDate, gridCount));
    }
}
