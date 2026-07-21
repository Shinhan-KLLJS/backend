package com.shinhan.klljs.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;

@Configuration
public class SwaggerConfig {

    /**
     * Swagger UI의 태그(도메인) 목록 순서. springdoc이 컨트롤러를 스캔하는 순서는 클래스패스
     * 스캔 순서에 좌우돼 예측할 수 없으므로, 도메인 단위로 읽히도록 순서를 직접 고정한다.
     * 여기 없는 태그가 나중에 추가되면 이 목록 뒤에 나타난다(springdoc 기본 동작 유지).
     */
    private static final List<String> TAG_ORDER = List.of(
            "인증",
            "사용자",
            "팀",
            "팀원 관리",
            "사업자등록증",
            "매체",
            "캠페인 등록",
            "캠페인 페이지",
            "홈 대시보드",
            "유동인구 적재(관리자)"
    );

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

    /** 태그를 TAG_ORDER 순서로 정렬해 Swagger UI에서 도메인 단위로 그룹지어 보이게 한다. */
    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            List<Tag> tags = openApi.getTags();
            if (tags == null) {
                return;
            }
            tags.sort(Comparator.comparingInt(tag -> {
                int index = TAG_ORDER.indexOf(tag.getName());
                return index < 0 ? Integer.MAX_VALUE : index;
            }));
        };
    }
}