package com.shinhan.klljs.domain.auth.handler;

import com.shinhan.klljs.domain.auth.config.AppAuthProperties;
import com.shinhan.klljs.domain.auth.dto.OAuth2LoginResult;
import com.shinhan.klljs.domain.auth.filter.OAuth2RedirectOriginFilter;
import com.shinhan.klljs.domain.auth.principal.CustomOAuth2Principal;
import com.shinhan.klljs.domain.auth.service.OAuth2LoginSuccessFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 팀 참가(초대 코드)는 이 흐름과 완전히 분리돼 있으므로 여기서 초대 문맥을 다루지 않는다.
 * session.invalidate()는 순수하게 OAuth 왕복용 임시 세션(state 보관 등)을 정리하는 목적이다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2LoginSuccessFacade successFacade;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AppAuthProperties properties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomOAuth2Principal principal = (CustomOAuth2Principal) authentication.getPrincipal();

        OAuth2LoginResult result = successFacade.complete(principal.getUserId());

        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshTokenCookie().toString());

        // 로그인 뒤 카카오 API를 사용하지 않으므로 카카오 토큰을 계속 들고 있을 이유가 없다
        authorizedClientService.removeAuthorizedClient("kakao", authentication.getName());

        HttpSession session = request.getSession(false);
        String redirectOrigin = null;
        if (session != null) {
            redirectOrigin = (String) session.getAttribute(OAuth2RedirectOriginFilter.SESSION_ATTRIBUTE);
            session.invalidate();
        }

        response.sendRedirect(properties.frontendLoginSuccessUrl(redirectOrigin));
    }
}
