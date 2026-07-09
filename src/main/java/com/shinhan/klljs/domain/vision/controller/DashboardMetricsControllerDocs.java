package com.shinhan.klljs.domain.vision.controller;

import com.shinhan.klljs.domain.vision.dto.AverageWatchTimeResponse;
import com.shinhan.klljs.domain.vision.dto.DemographicViewRatioResponse;
import com.shinhan.klljs.domain.vision.dto.HourlyAgeExposureResponse;
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
 * {@link DashboardMetricsController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 * 매핑 애노테이션은 컨트롤러에 그대로 두고, 여기에는 문서화용 애노테이션만 둔다.
 */
@Tag(
        name = "평균 시청시간·성별연령·시간대 노출도",
        description = "평균 시청시간, 성별·연령 시청 비율, 시간·연령별 노출도 3개 API. " +
                "campaignId + selectedPeriod/effectivePeriod/periodStatus + aggregationUnit/" +
                "aggregationCutoffTime/refreshIntervalSec 틀을 공유한다."
)
public interface DashboardMetricsControllerDocs {

    @Operation(
            summary = "평균 시청시간 조회",
            description = """
                    도넛 차트로 표시할 평균 시청시간과 시청시간 구간별 분포를 조회한다. `refreshIntervalSec`
                    (60초)에 맞춰 재조회를 권장한다. 1분 단위로 확정된 데이터만 집계한다.

                    ### 집계 범위
                    - 선택 기간에 **오늘이 포함**되면, 지금 진행 중인 분(아직 5초 데이터가 다 안 모였을 수 있음)은
                      제외한다. `aggregationCutoffTime`은 "지금 진행 중인 분의 시작 시각"이 된다
                      (예: 서버 시각 16:43:25면 `16:43:00`).
                    - 선택 기간이 **완전히 과거**(오늘 미포함)면 해당 날짜 전체(00:00~24:00 KST)를 사용하고,
                      `aggregationCutoffTime`은 조회 상한(선택 기간 마지막 날 다음날 00:00)이 된다. 이 경우
                      값이 더 안 바뀌므로 재조회하지 않아도 된다.

                    ### 계산식
                    5초 row의 `avg_dwell_sec`를 단순 평균하지 않고 가중 평균을 쓴다.
                    ```
                    averageWatchTimeSec = sum(dwell_sum_sec) / sum(lts_count)
                    ```
                    분모(주목인구 합)가 0이면 계산할 수 없으므로 `averageWatchTimeSec: null`로 응답한다.

                    ### `watchTimeBuckets`
                    4개 구간(1-2초/2-3초/3-4초/4초 이상)의 `count` 합계 대비 `ratio`(%)를 응답한다 -
                    4개 구간을 다 더하면 100%가 된다(구간 합계가 0이면 전부 `ratio: 0`).

                    ### `periodStatus == BEFORE_EXECUTION`일 때
                    `averageWatchTimeSec: null`, `watchTimeBuckets: []`.
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
                                "selectedPeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "effectivePeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "aggregationUnit": "MINUTE",
                                "aggregationCutoffTime": "2026-07-07T16:43:00+09:00",
                                "refreshIntervalSec": 60,
                                "averageWatchTimeSec": 1.6,
                                "watchTimeBuckets": [
                                  { "bucket": "1_TO_2S", "label": "1-2초", "count": 320, "ratio": 34.0 },
                                  { "bucket": "2_TO_3S", "label": "2-3초", "count": 280, "ratio": 29.8 },
                                  { "bucket": "3_TO_4S", "label": "3-4초", "count": 210, "ratio": 22.3 },
                                  { "bucket": "OVER_4S", "label": "4초 이상", "count": 131, "ratio": 13.9 }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<AverageWatchTimeResponse> getAverageWatchTime(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );

    @Operation(
            summary = "성별·연령 시청 비율 조회",
            description = """
                    화면 우측의 성별·연령 시청 비율 막대 그래프를 조회한다. LTS(주목인구) 성별·연령
                    집계 기반이다. `refreshIntervalSec`(3600초)에 맞춰 재조회를 권장한다.

                    ### 전체/남성/여성 토글
                    화면에 토글 버튼이 있지만 **버튼을 눌러도 API를 다시 호출하지 않는다** - 한 번의 응답에
                    세 뷰에 필요한 값을 모두 담아 내려주고, 프론트는 토글 상태에 따라 `ageGroups[]`의
                    필드 중 어떤 걸 막대 길이로 쓸지만 바꾼다.
                    - **전체 탭**: `totalRatio`를 막대 길이로, 막대 안을 `maleRatio`/`femaleRatio`로 두 색 채움
                    - **남성 탭**: `maleShareRatio`를 막대 길이로 (단색). 7개 연령대 합 = 100%
                    - **여성 탭**: `femaleShareRatio`를 막대 길이로 (단색). 7개 연령대 합 = 100%

                    ### 두 종류 비율의 분모가 다르다
                    - `maleRatio`/`femaleRatio`/`totalRatio`: **전체 시청자(남녀 합산)** 대비 비율 -
                      7개 연령대의 `totalRatio`를 다 더하면 100%
                    - `maleShareRatio`/`femaleShareRatio`: **그 성별 시청자만** 놓고 봤을 때의 비율 -
                      남성 7개 연령대 `maleShareRatio` 합 = 100%, 여성도 마찬가지

                    분모는 `lts_male_count`/`lts_female_count` 컬럼이 아니라 **7개 연령 컬럼의 합**을
                    쓴다 - 얼굴 인식은 됐는데 나이 추정만 실패한 케이스가 있으면 count 컬럼이 연령 컬럼
                    합보다 클 수 있어서, 연령 컬럼 합을 쓰면 이 문제와 무관하게 비율의 합이 항상 정확히
                    100%가 되기 때문이다.

                    `genderSummary.maleRatio`/`femaleRatio`는 이 집계 대상이 0명이면 `null`이다.
                    `ageGroups[]`의 비율 필드들은 분모가 0이면 `null`이 아니라 `0`으로 응답한다.

                    ### `periodStatus == BEFORE_EXECUTION`일 때
                    `genderSummary.maleRatio`/`femaleRatio`는 `null`, `ageGroups: []`.
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
                                "selectedPeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "effectivePeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "aggregationUnit": "HOUR",
                                "aggregationCutoffTime": "2026-07-07T16:00:00+09:00",
                                "refreshIntervalSec": 3600,
                                "genderSummary": { "maleRatio": 63.4, "femaleRatio": 36.6 },
                                "ageGroups": [
                                  { "ageGroup": "UNDER_10", "label": "0-9세", "maleRatio": 0.7, "femaleRatio": 0.5, "totalRatio": 1.2, "maleShareRatio": 1.1, "femaleShareRatio": 1.4 },
                                  { "ageGroup": "10S", "label": "10-19세", "maleRatio": 1.3, "femaleRatio": 0.8, "totalRatio": 2.1, "maleShareRatio": 2.0, "femaleShareRatio": 2.2 },
                                  { "ageGroup": "20S", "label": "20-29세", "maleRatio": 18.4, "femaleRatio": 11.8, "totalRatio": 30.2, "maleShareRatio": 29.0, "femaleShareRatio": 32.2 },
                                  { "ageGroup": "30S", "label": "30-39세", "maleRatio": 14.5, "femaleRatio": 7.9, "totalRatio": 22.4, "maleShareRatio": 22.9, "femaleShareRatio": 21.6 },
                                  { "ageGroup": "40S", "label": "40-49세", "maleRatio": 10.2, "femaleRatio": 6.1, "totalRatio": 16.3, "maleShareRatio": 16.1, "femaleShareRatio": 16.7 },
                                  { "ageGroup": "50S", "label": "50-59세", "maleRatio": 8.8, "femaleRatio": 5.2, "totalRatio": 14.0, "maleShareRatio": 13.9, "femaleShareRatio": 14.2 },
                                  { "ageGroup": "60_PLUS", "label": "60세 이상", "maleRatio": 9.5, "femaleRatio": 4.3, "totalRatio": 13.8, "maleShareRatio": 15.0, "femaleShareRatio": 11.7 }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<DemographicViewRatioResponse> getDemographicViewRatio(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );

    @Operation(
            summary = "시간·연령별 노출도 조회",
            description = """
                    시간대·연령대별 노출도 히트맵을 조회한다. OTS(노출인구) 성별·연령 집계 기반이다.
                    `refreshIntervalSec`(3600초)에 맞춰 재조회를 권장한다.

                    ### 시간 축 집계 방식
                    날짜는 버리고 **하루 중 시간대(0~23시)** 기준으로 묶는다 - 예를 들어 선택 기간이
                    3일이면, 그 3일의 "14시" 데이터를 전부 합쳐 하나의 14시 셀로 보여준다("하루 중
                    언제가 붐비는지" 패턴을 보여주는 게 목적이라 날짜별로 쪼개지 않는다).

                    ### `hours`/`ageGroups` 축과 `cells` 데이터의 관계
                    - `hours`: 화면에 표시할 시간 축. 매체 운영 시작 시각인 06시부터, 조회 기간이
                      완전히 과거면 23시까지, 오늘이 포함되면 지금 시각(KST)까지 표시한다 - 아직
                      데이터가 없는 진행 중인 시간대도 축에는 나오지만(빈 칸), 하루가 지나면서 축이
                      갑자기 늘어나 보이는 걸 막기 위함이다.
                    - `ageGroups`: 화면에 표시할 연령 축. 캠페인 상태와 무관하게 항상 7개 고정.
                    - `cells`: 실제 노출 데이터가 있는 (시간, 연령대) 조합만 포함한다. 노출이 0인
                      조합은 아예 배열에서 빠진다(0으로 채워 넣지 않음).

                    ### 전체/남성/여성 토글
                    화면에 토글 버튼이 있지만 버튼을 눌러도 API를 다시 호출하지 않는다 - `cells[]`에
                    세 뷰의 값을 모두 담아 내려주고, 프론트는 토글 상태에 따라 어떤 `exposureCount`/
                    `intensityLevel` 쌍을 색칠에 쓸지만 바꾼다 (전체 = `exposureCount`/`intensityLevel`,
                    남성 = `maleExposureCount`/`maleIntensityLevel`, 여성 = `femaleExposureCount`/`femaleIntensityLevel`).

                    ### `intensityLevel` 계산
                    3개 레벨(`intensityLevel`/`maleIntensityLevel`/`femaleIntensityLevel`)은 **서로
                    독립적으로** 정규화한다 - `maleIntensityLevel`은 이 응답의 `maleExposureCount`
                    최댓값 기준이지, 전체 탭 최댓값과는 무관하다. 남녀 인원수는 보통 전체보다 작으므로,
                    같은 기준으로 정규화하면 남성/여성 탭이 항상 옅게만 보이게 된다 - 각 뷰가 "그 성별
                    안에서 상대적으로 어디가 진한지"를 보여주는 게 목적이라 따로 정규화한다.
                    ```
                    max = 이 응답의 cells 중 해당 필드 최댓값
                    intensityLevel = exposureCount == 0 ? 0 : min(4, ceil(exposureCount / max * 4))
                    ```
                    각 뷰에서 가장 노출이 많은 셀은 항상 레벨 4다. 서버가 세 레벨을 전부 계산해서
                    내려주므로 프론트는 활성화된 탭 필드 값(0~4)에 색상만 매핑하면 된다.

                    ### `periodStatus == BEFORE_EXECUTION`일 때
                    `hours: []`, `cells: []`. `ageGroups`는 고정 축이라 이 경우에도 채워진다.
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
                                "selectedPeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "effectivePeriod": { "startDate": "2026-07-07", "endDate": "2026-07-07" },
                                "periodStatus": "IN_EXECUTION",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "aggregationUnit": "HOUR",
                                "aggregationCutoffTime": "2026-07-07T16:00:00+09:00",
                                "refreshIntervalSec": 3600,
                                "hours": ["06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16"],
                                "ageGroups": [
                                  { "ageGroup": "UNDER_10", "label": "0-9세" },
                                  { "ageGroup": "10S", "label": "10-19세" },
                                  { "ageGroup": "20S", "label": "20-29세" },
                                  { "ageGroup": "30S", "label": "30-39세" },
                                  { "ageGroup": "40S", "label": "40-49세" },
                                  { "ageGroup": "50S", "label": "50-59세" },
                                  { "ageGroup": "60_PLUS", "label": "60세 이상" }
                                ],
                                "cells": [
                                  { "hour": "06", "ageGroup": "20S", "exposureCount": 182, "intensityLevel": 2, "maleExposureCount": 108, "maleIntensityLevel": 2, "femaleExposureCount": 74, "femaleIntensityLevel": 2 },
                                  { "hour": "07", "ageGroup": "20S", "exposureCount": 255, "intensityLevel": 3, "maleExposureCount": 150, "maleIntensityLevel": 3, "femaleExposureCount": 105, "femaleIntensityLevel": 3 },
                                  { "hour": "08", "ageGroup": "20S", "exposureCount": 410, "intensityLevel": 4, "maleExposureCount": 240, "maleIntensityLevel": 4, "femaleExposureCount": 170, "femaleIntensityLevel": 4 }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<HourlyAgeExposureResponse> getHourlyAgeExposure(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );
}
