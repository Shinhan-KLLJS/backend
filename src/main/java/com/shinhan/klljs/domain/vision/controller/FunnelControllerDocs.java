package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.FunnelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;

/**
 * {@link FunnelController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 * 매핑 애노테이션은 컨트롤러에 그대로 두고, 여기에는 문서화용 애노테이션만 둔다.
 */
@Tag(
        name = "깔대기 그래프",
        description = "홈 대시보드의 깔대기 그래프 카드(유동/노출/주목 인구, 주목 전환률) 조회 API"
)
public interface FunnelControllerDocs {

    @Operation(
            summary = "깔대기 그래프 대시보드 조회",
            description = """
                    특정 캠페인과 기간에 대한 깔대기 그래프 지표(전체 유동인구 → 노출인구 → 주목인구 →
                    주목 전환률)를 조회한다. `refreshIntervalSec`(60초)에 맞춰 재조회를 권장한다.
                    기간 처리 규칙(`periodStatus`/`effectivePeriod`)은 상세정보 조회와 동일하다.

                    ### 4개 지표 정의
                    | 지표 | 응답 필드 | 정의 |
                    |---|---|---|
                    | 전체 유동인구 | `metrics.totalTrafficCount` | 매체 좌표가 속한 250m 구역의 서울시 공공데이터 일일 유동인구 합계 |
                    | 노출인구 | `metrics.exposedPopulationCount` | 선택 기간의 `vision_summary_5s.ots_count` 합계 |
                    | 주목인구 | `metrics.attentionPopulationCount` | 선택 기간의 `vision_summary_5s.lts_count` 합계 |
                    | 주목 전환률 | `metrics.attentionConversionRate` | `attentionPopulationCount / exposedPopulationCount * 100` (%) |

                    노출인구가 0이면 주목 전환률은 계산할 수 없으므로 `value: null`로 응답한다 (0으로 나누지 않음).

                    ### 전체 유동인구 (서울시 공공데이터)
                    `metrics.totalTrafficCount`는 매체 위경도로 계산한 250m 격자코드의 서울시
                    "250M격자 생활인구(내국인)" 하루 합계다(`grid_population_daily`, 관리자 API로 수동
                    적재 - 최종적으로는 스케줄러가 매일 적재한다). 원본 데이터는 발행까지 약 4일이 걸려
                    여유를 둔 **6일 지연**으로 매핑한다 - 예를 들어 오늘(KST) 조회의 `totalTrafficCount`는
                    실제로는 "오늘-6일"의 격자 데이터다. 노출인구/주목인구(카메라 관측치)와 출처·수집
                    방식이 다르므로 화면에서도 구분해서 보여주는 걸 권장한다.

                    ### `trafficArea`
                    - `areaId`/`gridSizeMeter`/`dataSource`/`isMock`/`dataAggregationUnit`은 매체 위치 정보라
                      캠페인이 집행 전이어도 값이 채워진다.
                    - `dataDateRange`(전체 유동인구 계산에 쓴 날짜 범위)만 `effectivePeriod`와 같은 값이고,
                      집행 전이면 `null`이다.

                    ### `aggregationCutoffTime`
                    집계에 포함된 데이터의 배타적 상한 시각이다. 5-1/6절은 1분(`MINUTE`) 단위라, 오늘이 포함된
                    조회면 "지금 진행 중인 분의 시작 시각"과 같다 (예: 서버 시각 16:43:25면 `16:43:00`).

                    ### 어제 대비 증가율 (`yesterdayComparison`)
                    `selected_start_date == selected_end_date == 오늘(KST)`이고 그 결과 `periodStatus`가
                    `IN_EXECUTION`일 때만(즉 조회 대상이 실제로 "오늘 하루"일 때만) 4개 지표 전부에 대해
                    `yesterdayComparison`을 함께 계산한다. 그 외에는 전부 `null`이다.

                    - 비교 기준은 **"어제 같은 시간대"**다. 예를 들어 오늘 `16:43`에 조회하면 오늘 집계 범위는
                      `00:00:00`~`16:43:00`, 어제 비교 범위는 `2026-07-06 00:00:00`~`2026-07-06 16:43:00`이다.
                    - `increaseRate = (오늘 값 - 어제 값) / 어제 값 * 100`
                    - 어제 값이 0이면 나눌 수 없으므로 `increaseRate`만 `null`로 응답한다(`baseValue: 0`은 그대로 내려줌).
                    - 전체 유동인구도 같은 방식이지만, 비교 기준일이 6일 지연 매핑을 거친다 - 오늘 값은
                      "오늘-6일" 격자 데이터, 비교값은 "어제-6일" 격자 데이터다.
                    - AFTER_EXECUTION 상태에서 우연히 오늘 날짜를 선택해도(이미 끝난 캠페인) 이 경우
                      `effectivePeriod`가 "오늘"이 아니라 캠페인 집행 기간 전체가 되므로 비교 자체를 하지 않는다.

                    ### `periodStatus == BEFORE_EXECUTION`일 때
                    4개 지표 전부 `value: null`, `yesterdayComparison: null`이다. `unit`(`"people"`/`"percent"`)은
                    이 경우에도 채워진다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (오늘 조회 예시 - 어제 대비 증가율 포함)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "campaignId": 1,
                                "selectedPeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "effectivePeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "aggregationUnit": "MINUTE",
                                "aggregationCutoffTime": "2026-07-07T16:43:00+09:00",
                                "refreshIntervalSec": 60,
                                "trafficArea": {
                                  "areaId": "다사52255350",
                                  "gridSizeMeter": 250,
                                  "dataSource": "SEOUL_OPEN_DATA",
                                  "isMock": false,
                                  "dataAggregationUnit": "DAY",
                                  "dataDateRange": { "startDate": "2026-07-07", "endDate": "2026-07-07" }
                                },
                                "metrics": {
                                  "totalTrafficCount": {
                                    "value": 50000,
                                    "unit": "people",
                                    "yesterdayComparison": { "baseDate": "2026-07-06", "baseValue": 48500, "increaseRate": 3.1 }
                                  },
                                  "exposedPopulationCount": {
                                    "value": 8123,
                                    "unit": "people",
                                    "yesterdayComparison": { "baseDate": "2026-07-06", "baseValue": 8164, "increaseRate": -0.5 }
                                  },
                                  "attentionPopulationCount": {
                                    "value": 123,
                                    "unit": "people",
                                    "yesterdayComparison": { "baseDate": "2026-07-06", "baseValue": 115, "increaseRate": 7.0 }
                                  },
                                  "attentionConversionRate": {
                                    "value": 1.5,
                                    "unit": "percent",
                                    "yesterdayComparison": { "baseDate": "2026-07-06", "baseValue": 1.4, "increaseRate": 7.1 }
                                  }
                                }
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<FunnelResponse> getFunnel(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );
}
