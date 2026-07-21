package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDeliveryResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDetailResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignListResponse;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
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
 * {@link DashboardCampaignController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 *
 * 컨트롤러는 이 인터페이스를 구현하기만 하고, 실제 스프링 MVC 매핑 애노테이션
 * (@GetMapping, @PathVariable, @RequestParam 등)은 컨트롤러 클래스 쪽에 그대로 둔다.
 * 이 인터페이스에는 Swagger 문서화용 애노테이션(@Operation, @Parameter, @ApiResponses)만
 * 두어서 "이 엔드포인트가 실제로 어떻게 매핑되는가"와 "이 엔드포인트를 어떻게 문서화하는가"를
 * 파일 단위로 분리한다. springdoc이 컨트롤러 메서드를 스캔할 때 이 인터페이스의 메서드
 * 애노테이션을 함께 병합해서 최종 문서를 만든다.
 *
 * 응답 래퍼 공통 규칙: 모든 응답은 {@code ApiResponse<T>}(isSuccess/code/message/result)로
 * 감싸져 있다. 아래 각 API 설명의 예시 JSON은 이 래퍼를 포함한 전체 응답 바디 기준이다.
 */
@Tag(
        name = "홈 대시보드",
        description = "홈 화면(대시보드)의 캠페인 목록/상세/송출정보 조회와 4개 카드(평균 시청시간·" +
                "성별연령·시간대 노출도, 깔대기 그래프, 노출·주목 흐름 그래프) API를 모두 모았다. " +
                "캠페인 자체의 수정·삭제는 '캠페인 페이지' 태그에서 다룬다."
)
public interface DashboardCampaignControllerDocs {

    @Operation(
            summary = "캠페인 목록 조회",
            description = """
                    홈 화면 상단의 캠페인명 드롭다운을 클릭했을 때 노출할 캠페인 목록을 조회한다.

                    ### 동작 방식
                    - 요청한 사용자가 **`ACTIVE` 상태로 속한 모든 팀**의 캠페인을 모아서 반환한다 (여러 팀에 속해 있으면 전부 합쳐서 나온다).
                    - **`IN_EXECUTION`(집행 중) / `AFTER_EXECUTION`(집행 후) 캠페인만 나타난다.** `REGISTRATION_FAILED`
                      (등록 실패) / `REGISTERED`(등록 완료, 집행 상태 전환 대기) / `BEFORE_EXECUTION`(집행 전)은
                      이 목록에 아예 노출되지 않는다.
                    - 어느 팀에도 속해 있지 않거나, 위 두 상태의 캠페인이 하나도 없으면 에러가 아니라
                      **빈 배열**(`campaigns: []`)을 반환한다.
                    - `keyword`가 있으면 `campaignName`에 **대소문자 무시 부분일치**로 필터링한다.
                    - `status`가 있으면 정확히 일치하는 상태의 캠페인만 필터링한다 (`IN_EXECUTION`/`AFTER_EXECUTION`
                      외의 값을 넘기면 항상 빈 배열).
                    - `keyword`/`status` 필터와 무관하게, **필터 적용 전 노출 대상 전체(IN_EXECUTION/AFTER_EXECUTION)**
                      기준으로 `isDefaultSelected`(기본 선택 캠페인)를 하나 정한다. 필터 때문에 원래 기본 선택
                      캠페인이 결과 목록에서 빠지더라도 다른 캠페인이 엉뚱하게 기본 선택으로 잡히는 문제를
                      막기 위해서다. 즉 필터링된 목록에는 `isDefaultSelected: true`인 캠페인이 아예 없을 수도 있다.

                    ### `isDefaultSelected` 우선순위
                    아래 순서대로 확인해서, 그 상태의 캠페인이 하나라도 있으면 즉시 확정한다 (동순위 내 기준도 함께 표기).
                    1. `IN_EXECUTION` 중 **가장 최근에 생성된** 캠페인
                    2. `AFTER_EXECUTION` 중 **집행 종료일이 가장 늦은** 캠페인
                    3. 위 두 상태가 전부 없으면(= 목록이 비어있으면) 기본 선택 캠페인도 없음

                    ### 정렬
                    응답 목록은 항상 **생성일(createdAt) 최신순**으로 정렬된다.

                    ### 캠페인 상태값(`status`)
                    응답에는 `IN_EXECUTION`(집행 중) / `AFTER_EXECUTION`(집행 후)만 나타난다. `REGISTRATION_FAILED`/
                    `REGISTERED`/`BEFORE_EXECUTION`은 이 목록 자체에서 제외되므로 응답에 등장하지 않는다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 소속 팀이 없거나 필터 조건에 맞는 캠페인이 없으면 `campaigns: []`",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "campaigns": [
                                  {
                                    "campaignId": 1,
                                    "campaignName": "여름 세일 프로모션",
                                    "brandName": "브랜드A",
                                    "executionStartDate": "2026-07-01",
                                    "executionEndDate": "2026-07-31",
                                    "status": "IN_EXECUTION",
                                    "isDefaultSelected": true
                                  }
                                ]
                              }
                            }
                            """))
            )
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<DashboardCampaignListResponse> getCampaigns(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인명 부분 검색어 (대소문자 무시). 생략하면 전체 조회", example = "여름 세일")
            String keyword,
            @Parameter(description = "캠페인 상태로 필터링. 생략하면 전체 상태 조회")
            CampaignStatus status
    );

    @Operation(
            summary = "캠페인 상세정보 조회",
            description = """
                    캠페인을 선택하거나, 이미 선택된 캠페인의 조회 기간을 변경했을 때 호출하는 기본 상세정보 API다.

                    ### 기간 처리 규칙 (campaign_id + 기간을 받는 모든 대시보드 API가 공통으로 따르는 규칙)
                    `selected_start_date`~`selected_end_date`(프론트가 고른 조회 기간)와 캠페인의 집행 기간
                    (`executionPeriod`)을 비교해서 `periodStatus`를 정하고, 그에 따라 `effectivePeriod`
                    (실제 집계에 쓴 기간)를 계산한다.

                    | periodStatus | 판정 기준 | effectivePeriod |
                    |---|---|---|
                    | `BEFORE_EXECUTION` | `selected_end_date` < 집행 시작일 | `null` |
                    | `IN_EXECUTION` | 선택 기간이 집행 기간과 하루 이상 겹침 | 선택 기간 ∩ 집행 기간 |
                    | `AFTER_EXECUTION` | `selected_start_date` > 집행 종료일 | 집행 기간 전체 |

                    예: 캠페인 집행 기간이 `2026-07-01`~`2026-07-31`이고 `selected_start_date=2026-06-30`,
                    `selected_end_date=2026-07-02`로 요청하면, 겹치는 날짜가 있으므로 `periodStatus=IN_EXECUTION`,
                    `effectivePeriod`는 `2026-07-01`~`2026-07-02`가 된다.

                    ### 응답에 함께 오는 3가지 기간 필드
                    - `executionPeriod`: 캠페인 집행 기간 그 자체 (조회 기간과 무관하게 항상 동일한 값)
                    - `selectedPeriod`: 프론트가 요청한 `selected_start_date`~`selected_end_date`를 그대로 echo
                    - `effectivePeriod`: 실제 집계 대상 기간. `BEFORE_EXECUTION`이면 `null`

                    ### ⚠️ `status` vs `periodStatus` 헷갈리지 않기
                    - `status`: 캠페인 **자체**의 상태 (DB에 저장된 값을 그대로 응답. 이 조회 요청과 무관)
                    - `periodStatus`: **이번 조회 요청**의 선택 기간이 집행 기간 대비 어디쯤인지 (요청마다 새로 계산됨)

                    캠페인이 지금 `IN_EXECUTION` 상태여도, 과거 날짜를 조회하면 그 요청의 `periodStatus`는
                    `AFTER_EXECUTION`이 될 수 있다. 두 값은 이름이 비슷하지만 완전히 다른 개념이다.

                    ### 기타 필드
                    - `mediaUnitId`: 이 캠페인에 연결된 매체 ID. 캠페인 등록 시 매체 선택이 필수라 항상 값이 있다.
                    - `mediaPhotoUrl`: 연결된 매체의 사진 URL.
                    - `dailyTargetPlayCount`: 하루 목표 송출 횟수.
                    - `imageUrl`: 캠페인 등록 시 업로드한 소재(이미지/영상) 조회 URL. 저장된 값이 아니라
                      매 요청마다 `creativeStorageKey`로 새로 계산한다.
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
                                "campaignName": "여름 세일 프로모션",
                                "brandName": "브랜드A",
                                "description": "여름 시즌 프로모션 캠페인입니다.",
                                "imageUrl": "https://cdn.example.com/campaign-creatives/7/550e8400-e29b-41d4-a716-446655440000",
                                "status": "IN_EXECUTION",
                                "mediaUnitId": 10,
                                "mediaPhotoUrl": "https://cdn.example.com/media-units/10.jpg",
                                "dailyTargetPlayCount": 480,
                                "executionPeriod": { "startDate": "2026-07-01", "endDate": "2026-07-31" },
                                "selectedPeriod": { "startDate": "2026-06-30", "endDate": "2026-07-02" },
                                "effectivePeriod": { "startDate": "2026-07-01", "endDate": "2026-07-02" },
                                "periodStatus": "IN_EXECUTION"
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<DashboardCampaignDetailResponse> getCampaignDetail(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );

    @Operation(
            summary = "캠페인 송출정보 조회",
            description = """
                    홈 화면 상단 대시보드 카드(캠페인 송출정보)를 조회한다. `refreshIntervalSec`(15초)에 맞춰
                    재조회하는 걸 권장한다. 기간 처리 규칙(`periodStatus`/`effectivePeriod`)은 상세정보 조회와 동일하다.

                    ### ⚠️ 현재 MVP 제약: 실제 송출 로그가 아니라 추정치
                    실제 송출 로그 테이블이 아직 없어서, `currentPlayCount`는 "매일 `playStartTime`(06:00)부터
                    `playIntervalSec`(15초) 간격으로 다운타임 없이 재생되고 있다"는 가정 하의 **시간 기반 추정치**다.
                    `isEstimated`는 지금은 항상 `true`다. 실제 송출 로그 테이블이 생기면 이 값들의 산출 방식이
                    실측치로 대체될 수 있으니, 프론트는 `isEstimated` 값 자체를 보고 UI 문구를 조정할 수 있게 만들어두면 좋다.

                    ### 계산 방식
                    - `periodTargetPlayCount` = `dailyTargetPlayCount` × `effectivePeriod` 일수
                    - `effectivePeriod`가 오늘을 포함하면: 오늘 이전 날짜는 목표치 100% 완료로 가정하고,
                      오늘 몫만 `playStartTime`부터 지금까지 경과 시간 ÷ `playIntervalSec`로 추정한다
                      (단, 하루 목표치를 넘지 않도록 상한을 둔다).
                    - `progressRate` = `currentPlayCount` / `periodTargetPlayCount` × 100 (%, `double`)
                    - `totalPlayTimeMin` = 총 재생 횟수 × 15초를 **분 단위로 변환**한 값. 초 단위가 아니라
                      **분 단위 정수만** 내려준다. "1시간 15분" 같은 화면 표시 문구 조립은 프론트가 담당한다.
                    - `nextIncrementAt`: 프론트가 카운트를 로컬에서 1회 증가시킬 다음 예상 시각(KST). 오늘 목표치에
                      이미 도달했으면(또는 애초에 오늘이 조회 기간에 없으면) `null`.

                    ### `periodStatus == BEFORE_EXECUTION`일 때
                    `currentPlayCount`/`periodTargetPlayCount`/`progressRate`/`totalPlayTimeMin`/`nextIncrementAt`
                    전부 `null`이다. `dailyTargetPlayCount`(캠페인 설정값 자체)는 이 경우에도 값이 있다.
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
                                "today": "2026-07-07",
                                "serverTime": "2026-07-07T16:43:25+09:00",
                                "playStartTime": "06:00:00",
                                "playIntervalSec": 15,
                                "refreshIntervalSec": 15,
                                "currentPlayCount": 2536,
                                "dailyTargetPlayCount": 2880,
                                "periodTargetPlayCount": 2880,
                                "progressRate": 88.05,
                                "totalPlayTimeMin": 634,
                                "nextIncrementAt": "2026-07-07T16:43:35+09:00",
                                "isEstimated": true
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`selected_start_date`가 `selected_end_date`보다 늦음 (code: `DASHBOARD_400_001`)"),
            @ApiResponse(responseCode = "403", description = "캠페인은 존재하지만 요청 사용자가 그 캠페인 소유 팀의 `ACTIVE` 멤버가 아님 (code: `CAMPAIGN_403_001`)"),
            @ApiResponse(responseCode = "404", description = "`campaign_id`에 해당하는 캠페인이 없음 (code: `CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<DashboardCampaignDeliveryResponse> getCampaignDelivery(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "캠페인 ID", example = "1")
            Long campaignId,
            @Parameter(description = "조회 시작일 (KST, yyyy-MM-dd)", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedStartDate,
            @Parameter(description = "조회 종료일 (KST, yyyy-MM-dd). `selected_start_date`보다 빠를 수 없다", example = "2026-07-07", required = true)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate selectedEndDate
    );
}
