package com.shinhan.klljs.domain.auth.filter;

import com.shinhan.klljs.global.config.AllowedOriginsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OAuth2RedirectOriginFilterTest {

    private final AllowedOriginsProperties allowedOrigins =
            new AllowedOriginsProperties("https://www.loovi.my", "http://localhost:5173");
    private final OAuth2RedirectOriginFilter filter = new OAuth2RedirectOriginFilter(allowedOrigins);

    @Test
    void storesAllowedOriginFromQueryParam() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.setParameter("redirect_origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getSession(false).getAttribute(OAuth2RedirectOriginFilter.SESSION_ATTRIBUTE))
                .isEqualTo("http://localhost:5173");
        verify(chain).doFilter(request, response);
    }

    @Test
    void fallsBackToRefererWhenQueryParamAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.addHeader(HttpHeaders.REFERER, "http://localhost:5173/welcome");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getSession(false).getAttribute(OAuth2RedirectOriginFilter.SESSION_ATTRIBUTE))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    void ignoresDisallowedOrigin_toPreventOpenRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.setParameter("redirect_origin", "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Object session = request.getSession(false);
        assertThat(session == null
                || request.getSession(false).getAttribute(OAuth2RedirectOriginFilter.SESSION_ATTRIBUTE) == null)
                .isTrue();
    }

    @Test
    void doesNothingForUnrelatedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setParameter("redirect_origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getSession(false)).isNull();
        verify(chain).doFilter(request, response);
    }
}
