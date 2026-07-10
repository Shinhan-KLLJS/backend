package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.HourlyGraphResponse;
import com.shinhan.klljs.domain.vision.dto.RealtimeGraphResponse;
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
import java.time.OffsetDateTime;

/**
 * {@link RealtimeGraphController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 * 매핑 애노테이션은 컨트롤러에 그대로 두고, 여기에는 문서화용 애노테이션만 둔다
 * (DashboardCampaignControllerDocs와 동일한 분리 방식).
 */
@Tag(
        name = "노출·주목 흐름 그래프",
        description = "홈 화면의 '노출/주목 흐름 그래프' 카드용 API 2종(5-1 실시간, 5-2 시간별 누적). " +
                "같은 화면 카드를 나눠 담당하며, 프론트는 아래 각 API 설명의 조건에 따라 둘 중 하나만 호출한다."
)
public interface RealtimeGraphControllerDocs {

    @Operation(
            summary = "5-1. 실시간 그래프 조회 (오늘, 5초 단위, 커서 폴링)",
            description = """
                    화면에 오늘 하루의 노출인구(OTS)/주목인구(LTS) 추이를 5초 단위로 그리기 위한 API다.

                    ### ⚠️ 다른 대시보드 API와 다른 점
                    - **날짜 파라미터가 없다.** 항상 서버 기준 **오늘(KST)만** 조회한다. 과거/여러 날짜 조회는
                      `GET .../realtime-graph/hourly`(5-2)를 대신 호출해야 한다.
                    - `selected_start_date`/`selected_end_date` 같은 기간 파라미터를 받는 다른 API들과 달리,
                      이 API는 **커서 기반 폴링** 방식이다.

                    ### 폴링(커서) 사용법
                    1. 화면 진입 시 `after_event_time` 없이 최초 호출 → 최근 `limit`개(기본 60, 최대 720)의
                       포인트를 오래된 순(ASC)으로 받는다.
                    2. 응답의 `lastEventTime`을 저장해뒀다가, 다음 호출에 `after_event_time`으로 넘긴다.
                    3. 이후에는 `pollIntervalSec`(5초)마다 직전 `lastEventTime`을 `after_event_time`으로
                       넘겨 반복 호출한다. 매번 새로 들어온 포인트만 받게 된다.
                    4. 캠페인이나 날짜가 바뀌면 커서(`after_event_time`)를 반드시 초기화(생략)하고 처음부터 다시 호출한다.

                    ### 겹쳐 조회(overlap)
                    SQS로 들어오는 Vision 메시지가 늦게 도착하거나 순서가 바뀔 수 있어서, 두 번째 호출부터는
                    서버가 넘겨받은 `after_event_time`보다 `overlapSec`(15초)만큼 더 이전 시점부터 조회한다
                    (중복 포인트가 살짝 다시 내려올 수 있다는 뜻 - 프론트는 `eventTime` 기준으로 중복을 걸러도 되고,
                    어차피 값이 같은 시점의 데이터라 다시 그려도 화면상 문제는 없다).

                    ### 기타 필드
                    - `dataDelaySec`: `lastEventTime`이 현재(`serverTime`)보다 얼마나 지연됐는지(초). Vision
                      장비/네트워크 지연을 화면에 경고로 보여주고 싶을 때 참고.
                    - `hasMore`: 이번 응답에 담기지 않은 더 최근 데이터가 있는지 (한 번에 `limit`개까지만 응답).
                    - `nextPollAfter`: 다음 폴링 권장 시각(`serverTime + pollIntervalSec`).
                    - `points[].source`: 항상 `"DB"` (SQS에서 막 수신한 실시간 스트림이 아니라, DB에 저장된
                      값을 조회한 것이라는 의미 - 항상 5초 이상 지연이 생길 수 있다는 뜻이기도 하다).
                    - 캠페인 집행 기간 밖(집행 전/후)이면 `points: []`, `periodStatus`만 참고.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "campaignId": 1,
                                "selectedDate": "2026-07-07",
                                "effectivePeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "pollIntervalSec": 5,
                                "overlapSec": 15,
                                "lastEventTime": "2026-07-07T16:43:20+09:00",
                                "nextPollAfter": "2026-07-07T16:43:30+09:00",
                                "dataDelaySec": 5,
                                "hasMore": false,
                                "points": [
                                  {
                                    "eventTime": "2026-07-07T16:43:15+09:00",
                                    "intervalSec": 5.0,
                                    "exposedPopulationCount": 12,
                                    "attentionPopulationCount": 3,
                                    "source": "DB"
                                  },
                                  {
                                    "eventTime": "2026-07-07T16:43:20+09:00",
                                    "intervalSec": 5.0,
                                    "exposedPopulationCount": 15,
                                    "attentionPopulationCount": 4,
                                    "source": "DB"
                                  }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<RealtimeGraphResponse> getRealtimeGraph(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "이 시각 이후(초과) 포인트만 조회하는 커서. 최초 호출 시 생략 " +
                    "(내부적으로 overlapSec만큼 더 이전부터 겹쳐서 조회한다)", example = "2026-07-07T16:43:20+09:00")
            OffsetDateTime afterEventTime,
            @Parameter(description = "한 번에 가져올 최대 포인트 수. 기본 60, 최대 720 (범위를 벗어나면 서버가 자동으로 clamp)", example = "60")
            Integer limit
    );

    @Operation(
            summary = "5-2. 시간별 누적 그래프 조회 (과거 기간, 1시간 단위)",
            description = """
                    같은 화면 카드(노출/주목 흐름 그래프)를 담당하지만, **과거 기간을 조회하거나 하루를 초과하는
                    기간을 조회할 때**(오늘 포함 여부와 무관) 5-1 대신 호출하는 API다. 5초 단위 실시간 데이터가
                    아니라 **1시간 단위로 합산**된 값을 응답하고, 커서 없이 매번 선택 기간 전체를 다시 조회한다.

                    ### 언제 5-1 대신 이 API를 부르나
                    조회 기간이 "오늘 하루"가 아니면(과거 날짜 포함 또는 여러 날짜 선택) 이 API를 쓴다.
                    프론트는 두 API 중 **하나만** 호출하면 되고, 같은 화면 카드에 결과를 그린다.

                    ### 재조회 필요 여부
                    `refreshIntervalSec`(3600)이 권장 재조회 주기지만, 선택 기간이 **완전히 과거**(오늘 미포함)면
                    값이 더 이상 바뀌지 않으므로 재조회 자체가 사실상 불필요하다. 선택 기간에 **오늘이 포함**되면
                    오늘에 해당하는 마지막(진행 중인) 시간대는 아직 확정 전이라 재조회가 필요하다.

                    ### `aggregationCutoffTime`
                    집계에 포함된 데이터의 **배타적 상한 시각**이다 (`event_time < aggregationCutoffTime`인 데이터까지
                    포함, 그 시각 자체는 미포함). 오늘이 포함된 조회면 "지금 진행 중인 시간대의 시작 시각"과 같다
                    (예: 서버 시각 16:43이면 `16:00:00` - 15:00~16:00 시간대까지만 확정 데이터로 포함).

                    ### `points` 배열
                    - `points[].eventTime`: 해당 1시간 구간의 **시작 시각**(KST).
                    - 데이터가 없는 시간대는 배열에서 통째로 빠진다 (0으로 채워서 내려주지 않음 - 5-1과 동일한 방식).
                    - `effectivePeriod`에 해당하는 시간대만 포함한다. `BEFORE_EXECUTION`이면 `points: []`.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "campaignId": 1,
                                "selectedPeriod": { "startDate": "2026-07-01", "endDate": "2026-07-05" },
                                "effectivePeriod": { "startDate": "2026-07-01", "endDate": "2026-07-05" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "aggregationUnit": "HOUR",
                                "aggregationCutoffTime": "2026-07-06T00:00:00+09:00",
                                "refreshIntervalSec": 3600,
                                "points": [
                                  { "eventTime": "2026-07-01T06:00:00+09:00", "exposedPopulationCount": 5820, "attentionPopulationCount": 412 },
                                  { "eventTime": "2026-07-01T07:00:00+09:00", "exposedPopulationCount": 6210, "attentionPopulationCount": 455 }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<HourlyGraphResponse> getHourlyGraph(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-01", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-05", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );
}
