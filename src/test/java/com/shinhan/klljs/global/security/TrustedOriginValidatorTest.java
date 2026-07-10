package com.shinhan.klljs.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedOriginValidatorTest {

    private final TrustedOriginValidator validator = new TrustedOriginValidator("https://www.loovi.my", "https://api.loovi.my");

    @Test
    void validate_passesWhenOriginMatches() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn("https://www.loovi.my");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_passesWhenRefererStartsWithAllowedOrigin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn(null);
        when(request.getHeader(HttpHeaders.REFERER)).thenReturn("https://www.loovi.my/login/success");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_passesWhenSwaggerOriginMatches() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn("https://api.loovi.my");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsUntrustedOrigin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn("https://evil.example.com");
        when(request.getHeader(HttpHeaders.REFERER)).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validate_rejectsWhenNoOriginOrRefererPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn(null);
        when(request.getHeader(HttpHeaders.REFERER)).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
