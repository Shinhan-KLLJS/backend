package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.TeamCampaignDetailResponse;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSort;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignStatusFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link TeamCampaignController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 * DashboardCampaignControllerDocs와 같은 이유로 매핑 애노테이션과 문서화 애노테이션을 분리한다.
 */
@Tag(name = "캠페인 페이지", description = "팀 캠페인 목록/상세/삭제 API")
public interface TeamCampaignControllerDocs {

    @Operation(
            summary = "팀 캠페인 목록 조회",
            description = """
                    팀의 "캠페인 페이지"(목록 화면)에 필요한 캠페인 목록을 조회한다 (campaign-page-api-spec.md 4절).

                    ### 접근 권한
                    요청한 사용자가 이 팀에 `ACTIVE` 상태로 속해 있어야 한다 (역할 제한 없음 - `MEMBER`도 조회 가능).

                    ### 상태 필터(`status`)
                    - `BEFORE_EXECUTION`을 넘기면 `REGISTERED`도 함께 포함해서 조회한다 - 등록 직후의
                      짧은 중간 상태라 사용자 눈에는 어차피 "아직 시작 안 한 캠페인"으로 보이는 게 자연스럽기 때문이다.
                    - 생략하면 이 팀 소유 캠페인 전체를 반환한다.

                    ### 정렬(`sort`)
                    - `NAME`(기본값): 캠페인명 오름차순(가나다순)
                    - `EXECUTION_RECENT`: 집행 시작일 내림차순
                    - `EXECUTION_OLDEST`: 집행 시작일 오름차순

                    ### `todayPlayCount` / `dailyTargetPlayCount` (화면의 "금일/누적")
                    - `todayPlayCount`(금일): 매 요청마다 서버 시간(KST) 기준으로 새로 계산되는 오늘 하루
                      추정 송출 횟수다. 집행 시작 전이면 0, 집행 기간이 이미 끝났으면(과거 날짜는 항상
                      목표를 100% 채웠다고 가정) `dailyTargetPlayCount`와 같은 값, 집행 중이면 06:00부터
                      15초 간격 추정치(하루 목표치가 상한)다.
                    - `dailyTargetPlayCount`(누적): 등록 시 설정한 하루 목표 송출 횟수를 저장된 값 그대로
                      보여준다 - 계산값이 아니라 상태와 무관하게 항상 같은 값이다.
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
                                "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀",
                                "campaigns": [
                                  {
                                    "campaignId": 31,
                                    "campaignName": "나이키 썸머 프로젝트 2026 07 ~ 08",
                                    "status": "IN_EXECUTION",
                                    "executionStartDate": "2026-07-11",
                                    "executionEndDate": "2026-08-15",
                                    "mediaLocationAddress": "서울특별시 강남구 테헤란로 1123",
                                    "todayPlayCount": 12,
                                    "dailyTargetPlayCount": 200
                                  }
                                ]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "403", description = "요청자가 이 팀 소속이 아님 (code: `TEAM_403_001`)"),
            @ApiResponse(responseCode = "404", description = "팀이 없음 (code: `TEAM_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<TeamCampaignListResponse> getCampaigns(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1")
            Long teamId,
            @Parameter(description = "캠페인 상태로 필터링. BEFORE_EXECUTION은 REGISTERED도 함께 포함. 생략하면 전체 상태 조회")
            TeamCampaignStatusFilter status,
            @Parameter(description = "캠페인명 부분 검색어 (대소문자 무시). 생략하면 전체 조회", example = "나이키")
            String keyword,
            @Parameter(description = "정렬 기준. 생략하면 NAME")
            TeamCampaignSort sort
    );

    @Operation(
            summary = "캠페인 상세정보 조회",
            description = """
                    캠페인 등록 3단계 "최종 확인" 화면과 같은 내용을 그대로 보여준다 (campaign-page-api-spec.md 5절).
                    `GET /api/v1/dashboard/campaigns/{campaignId}`(홈 대시보드용)와 달리 매체 조인 정보
                    (매체명/주소/규격/해상도/형태)와 소재 조회 URL(`creativeUrl`)을 포함한다.
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
                                "campaignId": 31,
                                "campaignName": "0711 나이키 썸머 프로모션 홍보 영상",
                                "brandName": "나이키 코리아",
                                "executionStartDate": "2026-07-11",
                                "executionEndDate": "2026-07-12",
                                "dailyTargetPlayCount": 100,
                                "description": "브랜드 인지도 확대를 주요 목표로 설정",
                                "creativeType": "VIDEO",
                                "creativeUrl": "https://cdn.example.com/campaign-creatives/42/550e8400-e29b-41d4-a716-446655440000",
                                "mediaUnitId": 12,
                                "mediaName": "파르나스 미디어타워 전광판",
                                "mediaLocationAddress": "서울 강남구 영동대로 513",
                                "mediaWidthMm": 81000,
                                "mediaHeightMm": 20000,
                                "mediaResolutionWidthPx": 1215,
                                "mediaResolutionHeightPx": 1792,
                                "mediaShapeTypes": ["FLAT", "VERTICAL"]
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "403", description = "요청자가 이 팀 소속이 아님 (code: `TEAM_403_001`)"),
            @ApiResponse(responseCode = "404", description = "팀이 없음 (`TEAM_404_001`) 또는 캠페인이 없거나 이 팀 소유가 아님 (`CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<TeamCampaignDetailResponse> getCampaignDetail(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1")
            Long teamId,
            @Parameter(description = "캠페인 ID", example = "31")
            Long campaignId
    );

    @Operation(
            summary = "캠페인 삭제",
            description = """
                    캠페인을 하드 삭제한다 (campaign-page-api-spec.md 6절) - `campaigns` row 자체가
                    지워지며, 되돌릴 수 없다. 상태(집행 전/중/완료)와 무관하게 항상 허용한다.
                    이 캠페인을 참조하던 `vision_summary_5s` 행은 DB의 `ON DELETE SET NULL`에 따라
                    `campaign_id`만 `NULL`로 바뀌고 그대로 남는다.

                    ### 접근 권한
                    팀의 `ACTIVE` `OWNER`/`ADMIN`만 가능하다 (캠페인 등록은 `MEMBER`도 가능하지만,
                    삭제는 별도 기준으로 `MEMBER`는 403).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공. `ApiResponse<Void>` 응답에서 null인 `result` 필드는 생략됨",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다."
                            }
                            """))
            ),
            @ApiResponse(responseCode = "403", description = "요청자가 이 팀 소속이 아님 (`TEAM_403_001`) 또는 `MEMBER`임 (`TEAM_403_003`)"),
            @ApiResponse(responseCode = "404", description = "팀이 없음 (`TEAM_404_001`) 또는 캠페인이 없거나 이 팀 소유가 아님 (`CAMPAIGN_404_001`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<Void> deleteCampaign(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1")
            Long teamId,
            @Parameter(description = "캠페인 ID", example = "31")
            Long campaignId
    );
}
