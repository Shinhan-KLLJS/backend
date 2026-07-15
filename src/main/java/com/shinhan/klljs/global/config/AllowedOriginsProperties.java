package com.shinhan.klljs.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CORS·Origin 검증·카카오 로그인 리다이렉트가 공통으로 신뢰하는 프론트 origin 목록이다.
 * {@code app.frontend-url}(기본/운영 origin)과 {@code app.additional-allowed-origins}
 * (콤마로 구분한 부가 origin - 로컬 프론트 등)를 하나로 합쳐 단일 출처로 유지한다.
 */
@Component
public class AllowedOriginsProperties {

    private final String primaryOrigin;
    private final Set<String> origins;

    public AllowedOriginsProperties(
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${app.additional-allowed-origins:}") String additionalAllowedOrigins
    ) {
        this.primaryOrigin = frontendUrl.trim();

        LinkedHashSet<String> allOrigins = new LinkedHashSet<>();
        allOrigins.add(primaryOrigin);
        for (String origin : additionalAllowedOrigins.split(",")) {
            if (!origin.isBlank()) {
                allOrigins.add(origin.trim());
            }
        }
        this.origins = Set.copyOf(allOrigins);
    }

    /** CORS 허용 origin 목록. 순서는 무의미하다 - 브라우저가 요청 Origin과 정확히 일치하는지만 본다. */
    public Set<String> origins() {
        return origins;
    }

    /** 로그인 리다이렉트 등에서 명시적 origin이 없을 때 쓰는 기본값(운영 프론트). */
    public String primaryOrigin() {
        return primaryOrigin;
    }

    public boolean isAllowed(String origin) {
        return origin != null && origins.contains(origin);
    }
}
