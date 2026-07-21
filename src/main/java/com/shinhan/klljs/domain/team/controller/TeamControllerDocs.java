package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.dto.TeamRenameRequest;
import com.shinhan.klljs.domain.team.dto.TeamRenameResponse;
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
@Tag(name = "팀", description = "팀 생성·수정 API")
public interface TeamControllerDocs {

    @Operation(
            summary = "팀 생성",
            description = """
                    사용자가 화면에서 확인한 사업자등록증 정보로 팀 + 팀원(OWNER) + 사업자등록 정보를
                    한 트랜잭션으로 생성한다. **진위확인·자동 판정은 하지 않는다 (MVP 스코프)** —
                    OCR이 읽고 사용자가 확인한 값이 그대로 저장된다.

                    ### 호출 순서
                    1. `POST /api/v1/teams/business-registration` — 파일 업로드 → `documentStorageKey`와 OCR 값 수신
                    2. 사용자가 화면에서 값 확인
                    3. **이 API** — 확인한 값 + `documentStorageKey`를 그대로 실어 보냄
                    4. `POST /api/v1/teams/{teamId}/invite-code` — 응답의 `teamId`로 곧바로 초대 코드 발급

                    ### 업태·종목은 요청에 없다
                    OCR이 읽은 원본이 `documentStorageKey` 토큰에 **서명되어** 들어 있고, 저장은 그 값을
                    쓴다 — 전송 구간에서 변조된 값이 OCR 원본 행세를 할 수 없다.

                    ### 형식이 어긋난 값도 400이 아니다
                    사업자등록번호·개업일자는 필수 입력·길이만 검사한다. 형식이 어긋나면 저장 시
                    형태만 정리한다(번호는 숫자 10자리로 통일 시도, 개업일은 파싱 실패 시 비워둠).
                    확인 화면의 목적은 "그대로 생성"이지 재검증이 아니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 생성 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "teamId": 12,
                                "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀"
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = """
                    - `COMMON_400_002`: 필수 필드 누락 또는 길이 초과
                    - `BUSINESS_400_001`: `documentStorageKey`가 유효하지 않음 (서명 위변조·만료·업로더 불일치).
                      **어느 쪽인지 구분해 알려주지 않는다** — 구분해 주면 토큰을 조금씩 바꿔가며 뚫는 판별기가 된다"""),
            @ApiResponse(responseCode = "401", description = "`Authorization` 헤더의 액세스 토큰이 만료·위조됨"),
            @ApiResponse(responseCode = "403", description = """
                    `BUSINESS_403_001`: 인증된 사용자의 users 행이 없음 — 정상 경로에서는 나오지 않는
                    경계 상황(데이터 정리 직후의 잔존 토큰 등)"""),
            @ApiResponse(responseCode = "409", description = "`BUSINESS_409_002`: 이미 소속된 팀이 있음 (사용자당 팀 하나)")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<TeamCreateResponse> createTeam(
            @Parameter(hidden = true) Jwt jwt,
            TeamCreateRequest request
    );

    @Operation(
            summary = "팀명 수정",
            description = """
                    팀명만 바꾼다. 팀의 `OWNER`/`ADMIN`만 호출할 수 있고, `MEMBER`는 403이다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "teamId": 12,
                                "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀"
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "`COMMON_400_002`: 팀명 누락 또는 200자 초과"),
            @ApiResponse(responseCode = "403", description = """
                    - `TEAM_403_001`: 요청자가 이 팀 소속이 아님
                    - `TEAM_403_004`: 팀 소속이지만 `MEMBER`라 팀 설정을 바꿀 권한이 없음"""),
            @ApiResponse(responseCode = "404", description = "`TEAM_404_001`: 팀이 없음"),
            @ApiResponse(responseCode = "409", description = "`TEAM_409_001`: 팀이 `ACTIVE` 상태가 아님")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<TeamRenameResponse> renameTeam(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "12")
            Long teamId,
            TeamRenameRequest request
    );
}
