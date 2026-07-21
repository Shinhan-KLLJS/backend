package com.shinhan.klljs.domain.auth.controller;

import com.shinhan.klljs.domain.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link AuthTokenController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
 */
@Tag(
        name = "인증",
        description = "카카오 로그인 이후의 Access/Refresh Token 발급·회전·로그아웃을 다룬다. 카카오 " +
                "로그인 자체(`GET /oauth2/authorization/kakao` → 카카오 인가 → " +
                "`GET /login/oauth2/code/kakao` 콜백)는 Spring Security의 OAuth2 로그인 필터 체인이 " +
                "처리하는 리다이렉트 흐름이라 일반 @RestController 엔드포인트가 아니고, 그래서 이 " +
                "목록에 나타나지 않는다 — 브라우저로 직접 로그인 시작 URL에 접속해야 하며, Swagger의 " +
                "\"Try it out\"으로는 테스트할 수 없다."
)
public interface AuthTokenControllerDocs {

    @Operation(
            summary = "Access Token 재발급",
            description = """
                    카카오 로그인 성공 리다이렉트 직후, 또는 Access Token이 만료됐을 때 프론트가 호출한다.
                    `refresh_token` HttpOnly 쿠키(경로 `/api/v1/auth`)를 읽어 검증하고, 새 Access
                    Token과 회전된 새 Refresh Token 쿠키를 함께 내려준다. 브라우저 리다이렉트 URL에는
                    토큰을 절대 싣지 않고 이 별도의 fetch 호출로만 전달한다.

                    ### 토큰 회전(Rotation)과 재사용 탐지
                    Refresh Token은 한 번 쓰면 즉시 폐기되고 새 토큰으로 교체되는 1회용 토큰이다. 이미
                    폐기된(= 한 번 쓰인) 토큰이 다시 제시되면 탈취로 간주해 같은 `token_family_id`의
                    모든 토큰을 한꺼번에 폐기한다 — 공격자와 정상 사용자 중 누가 진짜인지 서버가 구분할
                    수 없으므로, 양쪽 다 다시 로그인해야 한다.

                    ### CSRF 방어
                    쿠키는 브라우저가 요청마다 자동으로 첨부하므로, `Origin`/`Referer` 헤더가 허용된
                    프론트 오리진(`app.allowed-origins`) 또는 Swagger 자신의 오리진(`app.swagger-origin`)
                    중 하나와 일치하는지 먼저 확인한다 — 어느 쪽도 아니면 403.

                    ### 응답 형태가 다른 엔드포인트다
                    다른 API처럼 `ApiResponse.onSuccess(...)`를 직접 반환하지 않고
                    `ResponseEntity<ApiResponse<TokenResponse>>`로 감싼다 — 회전된 새 Refresh Token을
                    `Set-Cookie` 헤더에 실어야 하기 때문이다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공. 응답 헤더의 `Set-Cookie`로 새 refresh_token 쿠키가 함께 내려간다",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJrbGxqcyIsInN1YiI6IjEiLCJ0eXAiOiJhY2Nlc3MiLCJleHAiOjE3ODQ2MTAwMTcsImlhdCI6MTc4NDYwOTExN30.l6sIK9E69bv6PnbXzsCE2XpobrvavpPqdCO8UMQqaDY"
                              }
                            }
                            """))
            ),
            @ApiResponse(responseCode = "401", description = "`AUTH_401_001`: refresh_token 쿠키가 없거나, 존재하지 않거나, 만료·폐기됐거나(재사용 탐지 포함) 유효하지 않음"),
            @ApiResponse(responseCode = "403", description = "신뢰할 수 없는 오리진에서의 요청 (`Origin`/`Referer`가 허용 목록에 없음)")
    })
    ResponseEntity<com.shinhan.klljs.global.apiPayload.ApiResponse<TokenResponse>> refresh(
            @Parameter(hidden = true) String refreshToken,
            @Parameter(hidden = true) HttpServletRequest request
    );
}
