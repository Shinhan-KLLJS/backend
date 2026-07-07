package com.shinhan.klljs.domain.auth.controller;

import com.shinhan.klljs.domain.team.service.TeamInviteQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 팀 초대를 통한 로그인 시작 지점.
 * 원본 초대 코드는 OAuth state, 카카오 Redirect URL, 로그 어디에도 남기지 않는다 —
 * 검증 후 발급되는 inviteLinkId(내부 PK)만 임시 세션에 보관한다.
 */
@RestController
@RequiredArgsConstructor
public class OAuth2EntryController {

    private static final String INVITE_LINK_ID = "OAUTH_INVITE_LINK_ID";

    private final TeamInviteQueryService inviteQueryService;

    @GetMapping("/api/v1/auth/kakao/start")
    public void start(
            @RequestParam(required = false) String inviteToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        if (inviteToken != null && !inviteToken.isBlank()) {
            Long inviteLinkId = inviteQueryService.validateAndGetId(inviteToken);

            request.getSession(true).setAttribute(INVITE_LINK_ID, inviteLinkId);
        }

        response.sendRedirect("/oauth2/authorization/kakao");
    }
}
