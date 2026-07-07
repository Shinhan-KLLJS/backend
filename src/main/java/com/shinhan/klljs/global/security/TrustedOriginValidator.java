package com.shinhan.klljs.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Refresh/Logout처럼 쿠키가 브라우저에 의해 자동 첨부되는 상태 변경 요청에 대한 CSRF 방어(MVP 선택지 A).
 * CORS 설정과는 별개다 — CORS는 브라우저가 응답을 읽을 수 있는지를 제어하고,
 * 이 검증은 브라우저가 쿠키를 자동 첨부해 위조 요청을 보내는 것을 막는다.
 */
@Component
public class TrustedOriginValidator {

    private final Set<String> allowedOrigins;

    public TrustedOriginValidator(@Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.allowedOrigins = Set.of(frontendUrl);
    }

    public void validate(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);

        if (origin != null && allowedOrigins.contains(origin)) {
            return;
        }

        if (referer != null && allowedOrigins.stream().anyMatch(referer::startsWith)) {
            return;
        }

        throw new AccessDeniedException("Untrusted browser origin");
    }
}
