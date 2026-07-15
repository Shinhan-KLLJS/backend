package com.shinhan.klljs.global.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedOriginsPropertiesTest {

    @Test
    void origins_containsOnlyPrimaryWhenNoAdditionalConfigured() {
        AllowedOriginsProperties properties = new AllowedOriginsProperties("https://www.loovi.my", "");

        assertThat(properties.origins()).containsExactly("https://www.loovi.my");
        assertThat(properties.primaryOrigin()).isEqualTo("https://www.loovi.my");
    }

    @Test
    void origins_includesCommaSeparatedAdditionalOrigins() {
        AllowedOriginsProperties properties =
                new AllowedOriginsProperties("https://www.loovi.my", "http://localhost:5173, http://localhost:3000");

        assertThat(properties.origins())
                .containsExactlyInAnyOrder("https://www.loovi.my", "http://localhost:5173", "http://localhost:3000");
    }

    @Test
    void isAllowed_rejectsUnknownOrigin() {
        AllowedOriginsProperties properties = new AllowedOriginsProperties("https://www.loovi.my", "http://localhost:5173");

        assertThat(properties.isAllowed("https://evil.example.com")).isFalse();
        assertThat(properties.isAllowed(null)).isFalse();
        assertThat(properties.isAllowed("http://localhost:5173")).isTrue();
    }
}
