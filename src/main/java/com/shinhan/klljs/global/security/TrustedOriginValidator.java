package com.shinhan.klljs.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Refresh/Logout처럼 쿠키가 브라우저에 의해 자동 첨부되는 상태 변경 요청에 대한 CSRF 방어(MVP 선택지 A).
 * CORS 설정과는 별개다 — CORS는 브라우저가 응답을 읽을 수 있는지를 제어하고,
 * 이 검증은 브라우저가 쿠키를 자동 첨부해 위조 요청을 보내는 것을 막는다.
 */
@Component
public class TrustedOriginValidator {

    private final Set<String> allowedOrigins;

    public TrustedOriginValidator(
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${app.swagger-origin:}") String swaggerOrigin
    ) {
        this.allowedOrigins = allowedOrigins(frontendUrl, swaggerOrigin);
    }

    public void validate(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);

        if (origin != null && allowedOrigins.contains(origin)) {
            return;
        }

        String refererOrigin = refererOrigin(referer);
        if (refererOrigin != null && allowedOrigins.contains(refererOrigin)) {
            return;
        }

        throw new AccessDeniedException("Untrusted browser origin");
    }

    /**
     * Referer는 전체 URL(경로 포함)이라 문자열 startsWith로 비교하면
     * "https://loovi.my.evil.com"처럼 허용 origin을 접두사로 갖는 도메인에 우회당한다.
     * scheme+host+port만 재조합해 Origin 헤더와 동일한 기준(정확히 일치)으로 비교한다.
     */
    private String refererOrigin(String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            return port == -1 ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Set<String> allowedOrigins(String... origins) {
        LinkedHashSet<String> allowedOrigins = new LinkedHashSet<>();
        for (String origin : origins) {
            if (origin == null || origin.isBlank()) {
                continue;
            }
            allowedOrigins.add(origin.trim());
        }
        return Set.copyOf(allowedOrigins);
    }
}
