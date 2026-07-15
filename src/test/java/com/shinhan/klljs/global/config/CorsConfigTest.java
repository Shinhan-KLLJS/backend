package com.shinhan.klljs.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_allowsOnlyConfiguredFrontendOriginWithCredentials() {
        CorsConfigurationSource source = new CorsConfig()
                .corsConfigurationSource(new AllowedOriginsProperties("https://app.example.com", ""));

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE");
        assertThat(configuration.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type");
    }

    @Test
    void corsConfigurationSource_allowsAdditionalOriginsToo() {
        CorsConfigurationSource source = new CorsConfig()
                .corsConfigurationSource(new AllowedOriginsProperties("https://app.example.com", "http://localhost:5173"));

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactlyInAnyOrder("https://app.example.com", "http://localhost:5173");
    }
}
