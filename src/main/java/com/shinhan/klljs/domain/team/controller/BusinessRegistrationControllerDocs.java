package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link BusinessRegistrationController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 * 컨트롤러에는 스프링 MVC 매핑만 두고, 문서화 애노테이션은 전부 여기에 모은다
 * (DashboardCampaignControllerDocs와 같은 패턴).
 */
@Tag(
        name = "사업자등록증 검증",
        description = "사용자가 확정한 사업자등록증 정보를 국세청에 대조해 승인/반려/검토필요를 판정하는 API"
)
public interface BusinessRegistrationControllerDocs {

    @Operation(
            summary = "사업자등록증 제출 및 검증 (재제출)",
            description = """
                    OCR로 읽은 값을 사용자가 화면에서 확인·수정해 확정 제출하면, 서버가 국세청에 대조해 판정하고
                    결과를 `team_business_registrations`에 저장한다.

                    **이미 존재하는 팀의 재제출 전용이다.** 팀을 처음 만들 때의 검증은 팀 생성 API
                    (`POST /api/v1/teams`)가 자기 트랜잭션 안에서 수행한다 — 그쪽은 `accepted`가 아니면
                    팀 자체를 만들지 않고 400을 낸다. 권한은 `OWNER`만 있다.

                    ### 승인 조건
                    아래 **세 가지를 모두** 만족해야 `accepted`다.
                    1. 사업자등록증 진위 확인 통과 (사업자등록번호 + 개업일자 + 대표자명이 국세청 기록과 일치)
                    2. 현재 운영 중인 사업자 (계속사업자. 휴업자·폐업자는 반려)
                    3. 광고 관련 사업자 (업태·종목에 광고 관련 키워드 존재)

                    **진위 확인 통과는 영업 중이라는 뜻이 아니다.** 진짜 사업자등록증인데 이미 폐업한 경우가
                    실제로 있어서, 진위와 영업 상태를 따로 판단한다.

                    ### 판정 순서 (처음 일치하는 규칙에서 멈춤)
                    | # | 조건 | decisionStatus | reasonCode |
                    |---|---|---|---|
                    | 1 | 사업자번호·개업일자·대표자명 형식 오류 | `review_required` | `VALIDATION_INPUT_INCOMPLETE` |
                    | 2 | 국세청 미등록 번호 | `rejected` | `UNREGISTERED_BUSINESS` |
                    | 3 | 진위 확인 실패 | `rejected` | `INVALID_CERTIFICATE` |
                    | 4 | 휴업자·폐업자 | `rejected` | `INACTIVE_BUSINESS` |
                    | 5 | 업태·종목이 둘 다 비어 있음 | `review_required` | `ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE` |
                    | 6 | 광고 관련 사업자 아님 | `rejected` | `NON_ADVERTISING_BUSINESS` |
                    | 7 | "마케팅"·"홍보" 등 중간 신뢰도 키워드만 매칭 | `review_required` | `ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED` |
                    | 8 | 위 전부 통과 | `accepted` | `ACCEPTED` |

                    ### 입력 형식과 400의 경계
                    `businessNumber`는 하이픈이 있어도 되고(`123-45-67890`), `businessOpeningDate`는 `YYYYMMDD`와
                    `yyyy-MM-dd`를 모두 받는다. 서버가 숫자만 남겨 정규화한다. `representativeName`의 말미
                    `외 N명`(공동대표 표기)도 서버가 제거한다 — 국세청은 대표자 한 명만 받기 때문이다.

                    이 세 값이 비어 있거나 형식이 어긋나도 **400이 아니라 200 + `review_required`** 다
                    (위 1번 규칙). 400은 DB 컬럼 길이를 넘겼거나 `companyName`/`documentStorageKey`가
                    비었을 때만 난다.

                    ### verificationStatus vs decisionStatus
                    - `verificationStatus`: 이 제출 이후 **팀의 현재 상태** (`PENDING`/`APPROVED`/`REJECTED`)
                    - `decisionStatus`: **이번 검증**의 결론 (`accepted`/`rejected`/`review_required`)

                    매핑은 `accepted`→`APPROVED`, `rejected`→`REJECTED`, `review_required`→`PENDING`으로 고정이다.

                    ### 재제출
                    팀당 한 건만 유지되므로 재제출은 기존 행을 덮어쓴다. 단 **이미 `APPROVED`인 팀은 409로 막는다** —
                    잘못된 문서 한 번으로 승인 상태가 반려로 퇴행하는 것을 방지하기 위해서다.

                    ### ⚠️ 검증하지 않는 값
                    `companyName`, `businessType`, `businessItem`은 국세청이 확인해주지 않는다. 사용자가 화면에서
                    수정한 값이 그대로 판정에 쓰이므로, 실제로는 음식점업이어도 업태를 "광고대행"으로 고쳐 제출하면
                    `accepted`가 나올 수 있다. 원본 문서는 `documentStorageKey`로 보관되어 사후 감사는 가능하다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검증 완료. 반려·검토필요도 요청 처리 자체는 성공이므로 200이다",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "승인 (광고대행 업종)", value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "teamId": 1,
                                        "verificationStatus": "APPROVED",
                                        "decisionStatus": "accepted",
                                        "reasonCode": "ACCEPTED",
                                        "message": "사업자등록증 검증을 통과했습니다.",
                                        "advertising": {
                                          "classificationStatus": "matched",
                                          "confidence": "high",
                                          "matchedKeywords": ["광고대행", "광고물", "광고"]
                                        },
                                        "verifiedAt": "2026-07-10T16:43:25+09:00"
                                      }
                                    }
                                    """),
                            @ExampleObject(name = "반려 (폐업자)", value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "teamId": 1,
                                        "verificationStatus": "REJECTED",
                                        "decisionStatus": "rejected",
                                        "reasonCode": "INACTIVE_BUSINESS",
                                        "message": "현재 운영 중인 사업자가 아닙니다. (상태: 폐업자, 폐업일: 2024-06-24)",
                                        "advertising": {
                                          "classificationStatus": "not_matched",
                                          "confidence": "none",
                                          "matchedKeywords": []
                                        },
                                        "verifiedAt": null
                                      }
                                    }
                                    """),
                            @ExampleObject(name = "검토 필요 (마케팅만 매칭)", value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "teamId": 1,
                                        "verificationStatus": "PENDING",
                                        "decisionStatus": "review_required",
                                        "reasonCode": "ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED",
                                        "message": "광고업 여부가 명확하지 않아 검토가 필요합니다.",
                                        "advertising": {
                                          "classificationStatus": "matched",
                                          "confidence": "medium",
                                          "matchedKeywords": ["마케팅대행", "마케팅"]
                                        },
                                        "verifiedAt": null
                                      }
                                    }
                                    """)
                    })
            ),
            @ApiResponse(responseCode = "400", description = "`companyName`/`documentStorageKey` 누락 또는 필드 길이 초과 (code: `COMMON_400_002`)"),
            @ApiResponse(responseCode = "401", description = "`Authorization` 헤더의 액세스 토큰이 만료·위조됨"),
            @ApiResponse(responseCode = "403", description = """
                    - `BUSINESS_403_001`: 요청 사용자가 해당 팀의 `ACTIVE` 멤버가 아님.
                      **팀이 존재하지 않는 경우에도 같은 응답**이다 — 404로 구분해 주면 팀의 존재 여부가 새어 나간다.
                    - `BUSINESS_403_002`: 팀 멤버이긴 하나 `OWNER`가 아님 (`ADMIN`/`MEMBER`).
                      사업자등록증은 팀의 법적 신원이라 `OWNER`만 다룰 수 있다.

                    `Authorization` 헤더를 아예 보내지 않은 경우에도 CSRF 필터에 먼저 막혀 401이 아닌 403이 나온다."""),
            @ApiResponse(responseCode = "409", description = "이미 승인(`APPROVED`)된 팀의 재제출 (code: `BUSINESS_409_001`)"),
            @ApiResponse(responseCode = "500", description = "국세청 API 호출 실패. DB에는 아무것도 저장되지 않으므로 재시도하면 된다 (code: `BUSINESS_500_004`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<BusinessRegistrationSubmitResponse> submitBusinessRegistration(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "사업자등록증을 등록할 팀 ID", example = "1")
            Long teamId,
            BusinessRegistrationSubmitRequest request
    );
}
