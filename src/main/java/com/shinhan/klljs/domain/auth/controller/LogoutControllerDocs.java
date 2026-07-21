package com.shinhan.klljs.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * {@link LogoutController}의 Swagger(OpenAPI) 문서 전용 인터페이스.
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
public interface LogoutControllerDocs {

    @Operation(
            summary = "로그아웃",
            description = """
                    현재 브라우저 세션의 Refresh Token 하나만 폐기하고 쿠키를 지운다. **카카오 계정
                    연결 자체는 끊지 않는다** — `user_social_accounts`는 그대로 남고, 다음에 다시
                    카카오로 로그인하면 즉시 재로그인된다. 카카오 쪽 Access/Refresh Token은 로그인
                    직후 이미 제거했으므로 여기서 별도로 만료시킬 대상이 없다.

                    ### 멱등(idempotent)이다
                    쿠키가 없거나 이미 폐기된 토큰이 와도 에러 없이 그대로 204를 반환한다 — 이미
                    로그아웃된 상태에서 다시 호출해도 안전하다.

                    ### CSRF 방어
                    재발급 API와 동일하게 `Origin`/`Referer`가 허용된 오리진인지 먼저 확인한다.

                    ### 응답 형태가 다른 엔드포인트다
                    다른 API처럼 `ApiResponse` 봉투로 감싸지 않는다 — 성공 시 본문 없이
                    `204 No Content`를 반환하고, `Set-Cookie` 헤더로 즉시 만료되는 쿠키(`Max-Age=0`)를
                    내려 브라우저가 쿠키를 지우게 한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공(또는 이미 로그아웃된 상태) - 본문 없음"),
            @ApiResponse(responseCode = "403", description = "신뢰할 수 없는 오리진에서의 요청 (`Origin`/`Referer`가 허용 목록에 없음)")
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) String refreshToken,
            @Parameter(hidden = true) HttpServletRequest request
    );
}
