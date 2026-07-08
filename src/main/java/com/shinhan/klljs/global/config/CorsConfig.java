package com.shinhan.klljs.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트 주소만 정확히 허용한다 — Access-Control-Allow-Origin: * 는 allowCredentials(true)와
 * 함께 쓸 수 없고(브라우저가 거부), 이 프로젝트는 쿠키(Refresh Token)를 쓰므로 와일드카드를 쓰지 않는다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
