package com.shinhan.klljs.domain.auth.dto;

import org.springframework.http.ResponseCookie;

public record OAuth2LoginResult(
        ResponseCookie refreshTokenCookie
) {
}
