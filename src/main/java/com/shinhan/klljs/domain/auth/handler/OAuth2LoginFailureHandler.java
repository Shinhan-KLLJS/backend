package com.shinhan.klljs.domain.auth.handler;

import com.shinhan.klljs.domain.auth.config.AppAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 카카오 원본 에러 메시지·스택 트레이스는 서버 로그에만 남기고,
 * 프론트에는 6개의 안전한 코드 중 하나만 리다이렉트 쿼리로 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Set<String> KNOWN_SAFE_REASONS = Set.of(
            "USER_SUSPENDED", "USER_WITHDRAWN", "KAKAO_PROFILE_INVALID"
    );
    private static final String ACCESS_DENIED_ERROR_CODE = "access_denied";

    private final AppAuthProperties properties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("Kakao login failed", exception);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(properties.frontendLoginFailureUrl(mapToSafeReason(exception)));
    }

    private String mapToSafeReason(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oAuth2Exception)) {
            return "INTERNAL_ERROR";
        }

        String errorCode = oAuth2Exception.getError().getErrorCode();

        if (KNOWN_SAFE_REASONS.contains(errorCode)) {
            return errorCode;
        }
        if (ACCESS_DENIED_ERROR_CODE.equals(errorCode)) {
            return "KAKAO_CANCELLED";
        }
        return "KAKAO_AUTHENTICATION_FAILED";
    }
}
