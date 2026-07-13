package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link TeamController}의 Swagger 문서 전용 인터페이스.
 */
@Tag(name = "팀", description = "팀 생성 API")
public interface TeamControllerDocs {

    @Operation(
            summary = "팀 생성",
            description = """
                    사업자등록증 진위를 확인하고, **통과한 경우에만** 팀 + 팀원(OWNER) + 사업자등록 정보를
                    한 트랜잭션으로 생성한다.

                    ### 호출 순서
                    1. `POST /api/v1/teams/business-registration` — 파일 업로드 → `documentStorageKey`와 OCR 값 수신
                    2. 사용자가 화면에서 값 확인·수정
                    3. **이 API** — 확정한 값 + `documentStorageKey`를 그대로 실어 보냄
                    4. `POST /api/v1/teams/{teamId}/invite-code` — 응답의 `teamId`로 곧바로 초대 코드 발급

                    ### 검증에 실패하면 팀이 만들어지지 않는다
                    `accepted`가 아니면 **아무 row도 만들지 않고 400**을 낸다. 반려(rejected)뿐 아니라
                    **검토 필요(review_required)도 400**이다 — 수동 검토 대기열이 없어서, PENDING 상태의 팀을
                    만들어두면 "PENDING인 동안 팀을 쓸 수 있는가"를 정해야 하기 때문이다.

                    사유는 `code`로 구분한다(아래 400 참고). 상세 메시지(폐업일 등)는 `errorDetail`에 담긴다.

                    ### 재제출 API와 응답 규칙이 다르다
                    같은 검증 로직을 쓰지만 `POST /api/v1/teams/{teamId}/business-registration`(재제출)은
                    반려·검토필요도 **200**으로 응답한다. 이미 있는 행의 재평가 결과를 화면에 보여줘야 하기
                    때문이다. 팀 생성은 리소스 생성 실패라 400이 맞다.

                    ### 업태·종목은 요청에 없다
                    광고업 판단의 유일한 근거인데 국세청이 확인해주지 않는다. 폼으로 받으면 실제로는
                    음식점업인 사업자가 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다.
                    그래서 OCR이 읽은 원본을 `documentStorageKey` 토큰에 **서명해서** 넣어 나른다.

                    ### 사업자등록번호·개업일자·대표자명의 400 경계
                    - 빈 값 → `COMMON_400_002` (필수 입력)
                    - 비어있지 않으나 형식 오류(9자리 번호, `20240631` 같은 없는 날짜) → `BUSINESS_400_101`
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 생성 성공. 검증을 통과한 경우에만 도달한다",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "teamId": 12,
                                "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀",
                                "verificationStatus": "APPROVED"
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = """
                    **필수/형식**
                    - `COMMON_400_002`: 필수 필드 누락 또는 길이 초과
                    - `BUSINESS_400_001`: `documentStorageKey`가 유효하지 않음 (서명 위변조·만료·업로더 불일치).
                      **어느 쪽인지 구분해 알려주지 않는다** — 구분해 주면 토큰을 조금씩 바꿔가며 뚫는 판별기가 된다

                    **검증 미통과 (팀이 만들어지지 않음)**
                    - `BUSINESS_400_101`: 사업자번호·개업일자·대표자명 형식 오류
                    - `BUSINESS_400_102`: 국세청 미등록 번호
                    - `BUSINESS_400_103`: 진위 확인 실패
                    - `BUSINESS_400_104`: 휴업자·폐업자 (`errorDetail`에 폐업일)
                    - `BUSINESS_400_105`: OCR이 업태·종목을 못 읽음 → **재업로드 필요** (폼에서 고칠 수 없다)
                    - `BUSINESS_400_106`: 광고 관련 사업자가 아님
                    - `BUSINESS_400_107`: 광고업 여부 불명확 ("마케팅"·"홍보"만 매칭) → 고객센터 문의
                    - `BUSINESS_400_108`: 진위 확인 미완료"""),
            @ApiResponse(responseCode = "401", description = "`Authorization` 헤더의 액세스 토큰이 만료·위조됨"),
            @ApiResponse(responseCode = "500", description = "국세청 API 호출 실패. 아무것도 저장되지 않으므로 재시도하면 된다 (`BUSINESS_500_004`)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<TeamCreateResponse> createTeam(
            @Parameter(hidden = true) Jwt jwt,
            TeamCreateRequest request
    );
}
