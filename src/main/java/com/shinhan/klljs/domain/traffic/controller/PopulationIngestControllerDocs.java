package com.shinhan.klljs.domain.traffic.controller;

import com.shinhan.klljs.domain.traffic.dto.PopulationIngestResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

/** {@link PopulationIngestController}의 Swagger 계약이다. */
@Tag(name = "유동인구 적재(관리자)", description = "서울시 250m격자 생활인구(내국인) 데이터를 수동으로 적재하는 관리자용 API")
public interface PopulationIngestControllerDocs {

    @Operation(
            summary = "생활인구 격자 데이터 적재",
            description = """
                    서울 열린데이터광장의 "서울특별시 250M격자 생활인구(내국인)"(OA-22784) 일별 원본
                    ZIP을 내려받아 격자코드별 하루 합계(0~23시 생활인구합계 합)로 집계한 뒤
                    `grid_population_daily`에 upsert한다.

                    데이터는 발행까지 약 4일이 걸리므로(공공데이터포털 안내), 여유를 둔 6일 지연
                    규칙으로 `date`를 생략하면 서버가 "오늘(KST) - 6일"을 자동 계산해서 적재한다.
                    이미 적재된 (격자코드, 날짜) 조합은 값을 덮어쓰므로 같은 날짜를 다시 호출해도 안전하다.

                    지금은 Swagger에서 수동으로 트리거하는 API이고, 최종적으로는 스케줄러가 매일
                    자동 호출하도록 얹을 예정이다.
                    """
    )
    ApiResponse<PopulationIngestResponse> ingest(
            @Parameter(description = "적재할 데이터 기준일(yyyy-MM-dd). 생략하면 오늘(KST)-6일", example = "2026-07-10")
            LocalDate date
    );
}
