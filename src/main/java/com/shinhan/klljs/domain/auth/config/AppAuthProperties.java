package com.shinhan.klljs.domain.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// 리다이렉트 URL을 만드는 AppAuthProperties
public class AppAuthProperties {

    private static final String LOGIN_SUCCESS_PATH = "/login/success";

    private final String frontendUrl;

    public AppAuthProperties(@Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String frontendLoginSuccessUrl() {
        return frontendUrl + LOGIN_SUCCESS_PATH;
    }
}
