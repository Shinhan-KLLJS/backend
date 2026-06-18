package com.shinhan.klljs.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // 1. 문서 기본 정보 설정
        Info info = new Info()
                .title("Klljs API 명세서")
                .description("신한 프로젝트 klljs 백엔드 API 문서입니다.")
                .version("v0.0.1");

        // 2. Spring Security(JWT 등)를 사용하는 경우 Swagger에서 인증 버튼을 활성화하기 위한 설정
        String securityJwtName = "JWT 인증 tokens";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securityJwtName);

        Components components = new Components()
                .addSecuritySchemes(securityJwtName, new SecurityScheme()
                        .name(securityJwtName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}