package com.shinhan.klljs.domain.auth.controller;

import com.shinhan.klljs.domain.auth.dto.TokenResponse;
import com.shinhan.klljs.domain.auth.exception.AuthErrorCode;
import com.shinhan.klljs.domain.auth.service.RefreshTokenService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.security.JwtTokenService;
import com.shinhan.klljs.global.security.TrustedOriginValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 성공 Redirect 직후 프론트가 호출해 Access Token을 받아가는 엔드포인트(19절).
 * 브라우저 리다이렉트 URL에는 토큰을 절대 싣지 않고, 이 별도의 fetch 호출로만 전달한다.
 */
@RestController
@RequiredArgsConstructor
public class AuthTokenController {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final TrustedOriginValidator trustedOriginValidator;

    @PostMapping("/api/v1/auth/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request
    ) {
        trustedOriginValidator.validate(request);

        if (refreshToken == null) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        String accessToken = jwtTokenService.generateAccessToken(rotated.userId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenService.buildCookie(rotated.rawToken()).toString())
                .body(ApiResponse.onSuccess(GeneralSuccessCode.OK, new TokenResponse(accessToken)));
    }
}
