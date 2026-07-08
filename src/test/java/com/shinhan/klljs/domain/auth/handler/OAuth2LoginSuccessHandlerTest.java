package com.shinhan.klljs.domain.auth.handler;

import com.shinhan.klljs.domain.auth.config.AppAuthProperties;
import com.shinhan.klljs.domain.auth.dto.OAuth2LoginResult;
import com.shinhan.klljs.domain.auth.principal.CustomOAuth2Principal;
import com.shinhan.klljs.domain.auth.service.OAuth2LoginSuccessFacade;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginSuccessHandlerTest {

    private final OAuth2LoginSuccessFacade facade = mock(OAuth2LoginSuccessFacade.class);
    private final OAuth2AuthorizedClientService authorizedClientService = mock(OAuth2AuthorizedClientService.class);
    private final AppAuthProperties properties = new AppAuthProperties("https://app.example.com");

    private final OAuth2LoginSuccessHandler handler =
            new OAuth2LoginSuccessHandler(facade, authorizedClientService, properties);

    @Test
    void onAuthenticationSuccess_setsCookieRemovesKakaoClientInvalidatesSessionAndRedirects() throws Exception {
        CustomOAuth2Principal principal = new CustomOAuth2Principal(42L, "123456789", Map.of("id", 123456789L));
        Authentication authentication = new TestingAuthenticationToken(principal, null);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "raw-value").httpOnly(true).build();
        when(facade.complete(42L)).thenReturn(new OAuth2LoginResult(cookie));

        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=raw-value");
        verify(authorizedClientService).removeAuthorizedClient(eq("kakao"), eq("123456789"));
        assertThatThrownBy(() -> session.getAttribute("anything")).isInstanceOf(IllegalStateException.class);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com/login/success");
    }
}
