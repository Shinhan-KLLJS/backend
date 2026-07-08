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

    public String frontendLoginSuccessUrl() {
        return frontendUrl + LOGIN_SUCCESS_PATH;
    }

    public String frontendLoginFailureUrl(String reason) {
        String encodedReason = URLEncoder.encode(reason, StandardCharsets.UTF_8);
        return frontendUrl + LOGIN_FAILURE_PATH + "?reason=" + encodedReason;
    }
}
