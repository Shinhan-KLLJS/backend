package com.shinhan.klljs.domain.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppAuthPropertiesTest {

    @Test
    void frontendLoginSuccessUrl_fallsBackToDefaultFrontendUrlWhenNoRedirectOrigin() {
        AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

        assertThat(properties.frontendLoginSuccessUrl(null))
                .isEqualTo("https://app.example.com/login/success");
    }

    @Test
    void frontendLoginSuccessUrl_usesRedirectOriginWhenPresent() {
        AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

        assertThat(properties.frontendLoginSuccessUrl("http://localhost:5173"))
                .isEqualTo("http://localhost:5173/login/success");
    }

    @Test
    void frontendLoginFailureUrl_appendsLoginFailurePathWithEncodedReason() {
        AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

        assertThat(properties.frontendLoginFailureUrl(null, "USER_SUSPENDED"))
                .isEqualTo("https://app.example.com/login/failure?reason=USER_SUSPENDED");
    }

    @Test
    void frontendLoginFailureUrl_usesRedirectOriginWhenPresent() {
        AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

        assertThat(properties.frontendLoginFailureUrl("http://localhost:5173", "USER_SUSPENDED"))
                .isEqualTo("http://localhost:5173/login/failure?reason=USER_SUSPENDED");
    }
}
