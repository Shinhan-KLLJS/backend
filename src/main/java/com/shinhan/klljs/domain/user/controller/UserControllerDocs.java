package com.shinhan.klljs.domain.user.controller;

import com.shinhan.klljs.domain.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link UserController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 */
@Tag(name = "사용자", description = "인증된 사용자 자신의 정보를 조회하는 API.")
public interface UserControllerDocs {

    @Operation(
            summary = "내 정보 조회",
            description = """
                    Access Token(`Authorization: Bearer`)이 정상 동작하는지 확인하는 용도로도 쓰는,
                    가장 단순한 인증 확인 엔드포인트다. JWT의 `sub` 클레임(발급 시 내부 userId로 채운
                    값)을 그대로 신뢰한다 — 서명 검증을 통과했다는 것 자체가 우리가 발급한 토큰이라는
                    보증이므로 별도로 DB에서 재확인하지 않는다.

                    ### `hasTeam` / `teamId`
                    현재는 "사용자는 팀 하나에만 속한다"는 단순화된 가정으로 응답을 구성한다 - 데이터
                    모델 자체는 다대다 소속을 허용하지만, 여러 `ACTIVE` 팀에 속해 있어도 그중 첫 번째
                    팀만 대표로 내려준다. 소속 팀이 하나도 없으면 `hasTeam: false`, `teamId: null`.
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
                                "id": 1,
                                "displayName": "홍길동",
                                "email": "user@example.com",
                                "profileImageUrl": "https://k.kakaocdn.net/dn/example/profile.jpg",
                                "status": "ACTIVE",
                                "hasTeam": true,
                                "teamId": 12
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 만료·위조됨")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<UserMeResponse> me(
            @Parameter(hidden = true) Jwt jwt
    );
}
