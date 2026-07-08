package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.dto.OAuth2LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 로그인 성공 후 해야 할 일을 조율한다. 이 시점엔 Access Token을 만들지 않는다 —
 * Refresh Token 쿠키만 심어두고, Access Token은 프론트가 곧이어 호출하는
 * /api/v1/auth/token/refresh에서 발급한다(19절 참고).
 */
@Service
@RequiredArgsConstructor
public class OAuth2LoginSuccessFacade {

    private final RefreshTokenService refreshTokenService;

    public OAuth2LoginResult complete(Long userId) {
        String rawToken = refreshTokenService.issue(userId);
        ResponseCookie cookie = refreshTokenService.buildCookie(rawToken);
        return new OAuth2LoginResult(cookie);
    }
}
