package com.shinhan.klljs.domain.auth.filter;

import com.shinhan.klljs.global.config.AllowedOriginsProperties;
import com.shinhan.klljs.global.security.RefererOriginParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 카카오 로그인 시작 요청({@code /oauth2/authorization/kakao})을 어느 프론트 origin이 걸었는지
 * 세션에 남겨서, 로그인 성공/실패 뒤 그 origin으로 돌려보낼 수 있게 한다(issue #41 - 로컬/배포
 * 프론트 동시 지원). Kakao 콜백(state 왕복) 사이에 값을 들고 가야 하므로 세션을 쓴다.
 *
 * <p>{@code redirect_origin} 쿼리 파라미터를 우선하고, 없으면 Referer로 추정한다 - 로그인 버튼이
 * 단순 링크/네비게이션이면 프론트가 아무것도 안 해도 Referer만으로 동작한다.</p>
 *
 * <p>허용 목록에 없는 origin은 무시하고 기본(운영) 프론트로 폴백한다 - 그렇지 않으면 이 파라미터가
 * open redirect 통로가 된다.</p>
 */
@Component
public class OAuth2RedirectOriginFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = "oauth2RedirectOrigin";
    private static final String AUTHORIZATION_REQUEST_PATH = "/oauth2/authorization/kakao";
    private static final String REDIRECT_ORIGIN_PARAM = "redirect_origin";

    private final AllowedOriginsProperties allowedOrigins;

    public OAuth2RedirectOriginFilter(AllowedOriginsProperties allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (AUTHORIZATION_REQUEST_PATH.equals(request.getRequestURI())) {
            String origin = resolveOrigin(request);
            if (allowedOrigins.isAllowed(origin)) {
                request.getSession(true).setAttribute(SESSION_ATTRIBUTE, origin);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveOrigin(HttpServletRequest request) {
        String param = request.getParameter(REDIRECT_ORIGIN_PARAM);
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return RefererOriginParser.parse(request.getHeader(HttpHeaders.REFERER));
    }
}
