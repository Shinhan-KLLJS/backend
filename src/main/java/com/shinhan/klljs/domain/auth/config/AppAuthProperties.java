package com.shinhan.klljs.domain.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
// 리다이렉트 URL을 만드는 AppAuthProperties
public class AppAuthProperties {

    private static final String LOGIN_SUCCESS_PATH = "/login/success";
    private static final String LOGIN_FAILURE_PATH = "/login/failure";

    private final String frontendUrl;

    public AppAuthProperties(@Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    /**
     * @param redirectOrigin 로그인을 시작한 프론트 origin(허용 목록 검증을 이미 통과한 값). null이면
     *                       기본(운영) 프론트로 돌려보낸다 - 로그인 시작 요청에 origin 단서가 없었거나
     *                       허용되지 않은 origin이었던 경우다.
     */
    public String frontendLoginSuccessUrl(String redirectOrigin) {
        return resolveOrigin(redirectOrigin) + LOGIN_SUCCESS_PATH;
    }

    public String frontendLoginFailureUrl(String redirectOrigin, String reason) {
        String encodedReason = URLEncoder.encode(reason, StandardCharsets.UTF_8);
        return resolveOrigin(redirectOrigin) + LOGIN_FAILURE_PATH + "?reason=" + encodedReason;
    }

    private String resolveOrigin(String redirectOrigin) {
        return (redirectOrigin != null && !redirectOrigin.isBlank()) ? redirectOrigin : frontendUrl;
    }
}
