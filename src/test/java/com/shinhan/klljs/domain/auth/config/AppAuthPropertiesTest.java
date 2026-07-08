package com.shinhan.klljs.domain.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppAuthPropertiesTest {

    @Test
    void frontendLoginSuccessUrl_appendsLoginSuccessPathToFrontendUrl() {
        AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

        assertThat(properties.frontendLoginSuccessUrl())
                .isEqualTo("https://app.example.com/login/success");
    }
}
