package com.shinhan.klljs.domain.auth.handler;

import com.shinhan.klljs.domain.auth.config.AppAuthProperties;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2LoginFailureHandlerTest {

    private final OAuth2LoginFailureHandler handler =
            new OAuth2LoginFailureHandler(new AppAuthProperties("https://app.example.com"));

    @Test
    void redirectsWithKakaoCancelledForAccessDenied() throws Exception {
        String redirectedUrl = handle(new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=KAKAO_CANCELLED");
    }

    @Test
    void redirectsWithUserSuspendedPassedThroughAsIs() throws Exception {
        String redirectedUrl = handle(new OAuth2AuthenticationException("USER_SUSPENDED"));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=USER_SUSPENDED");
    }

    @Test
    void redirectsWithGenericKakaoAuthenticationFailedForUserWithdrawnSinceItCanNoLongerBeThrown() throws Exception {
        // SocialLoginService는 더 이상 WITHDRAWN을 차단하지 않으므로(자동 재활성화),
        // 이 코드가 실제로 발생하지는 않지만 알려지지 않은 코드와 동일하게 처리되는지 확인한다.
        String redirectedUrl = handle(new OAuth2AuthenticationException("USER_WITHDRAWN"));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=KAKAO_AUTHENTICATION_FAILED");
    }

    @Test
    void redirectsWithKakaoProfileInvalidPassedThroughAsIs() throws Exception {
        String redirectedUrl = handle(new OAuth2AuthenticationException("KAKAO_PROFILE_INVALID"));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=KAKAO_PROFILE_INVALID");
    }

    @Test
    void redirectsWithGenericKakaoAuthenticationFailedForUnknownOAuth2ErrorCode() throws Exception {
        String redirectedUrl = handle(new OAuth2AuthenticationException(new OAuth2Error("invalid_grant")));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=KAKAO_AUTHENTICATION_FAILED");
    }

    @Test
    void redirectsWithInternalErrorForNonOAuth2Exception() throws Exception {
        String redirectedUrl = handle(new BadCredentialsException("unexpected"));

        assertThat(redirectedUrl).isEqualTo("https://app.example.com/login/failure?reason=INTERNAL_ERROR");
    }

    @Test
    void invalidatesExistingSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new OAuth2AuthenticationException("USER_SUSPENDED"));

        assertThatThrownBy(() -> session.getAttribute("anything"))
                .isInstanceOf(IllegalStateException.class);
    }

    private String handle(org.springframework.security.core.AuthenticationException exception) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        return response.getRedirectedUrl();
    }
}
