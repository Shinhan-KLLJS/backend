package com.shinhan.klljs.domain.user.controller;

import com.shinhan.klljs.domain.user.dto.UserMeResponse;
import com.shinhan.klljs.domain.user.service.UserQueryService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access Token으로 보호되는 API가 실제로 동작하는지 확인하기 위한 테스트용 엔드포인트.
 * JWT의 sub 클레임(발급 시 내부 userId로 채운 값)을 그대로 신뢰한다 —
 * 서명 검증을 통과했다는 것 자체가 우리가 발급한 토큰이라는 보증이다.
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;

    @GetMapping("/api/v1/users/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        UserMeResponse response = userQueryService.getMe(userId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
