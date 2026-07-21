package com.shinhan.klljs.domain.auth.controller;

import com.shinhan.klljs.domain.auth.service.RefreshTokenService;
import com.shinhan.klljs.global.security.TrustedOriginValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 로그아웃만 담당한다 — 카카오 연결(user_social_accounts)은 그대로 둔다.
 * 카카오 쪽 토큰은 로그인 직후 이미 제거되므로 여기서 별도로 만료시킬 것이 없다.
 */
@RestController
@RequiredArgsConstructor
public class LogoutController implements LogoutControllerDocs {

    private final RefreshTokenService refreshTokenService;
    private final TrustedOriginValidator trustedOriginValidator;

    @Override
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request
    ) {
        trustedOriginValidator.validate(request);

        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenService.expireCookie().toString())
                .build();
    }
}
