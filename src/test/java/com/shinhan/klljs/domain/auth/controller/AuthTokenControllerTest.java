package com.shinhan.klljs.domain.auth.controller;

import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import com.shinhan.klljs.domain.auth.service.RefreshTokenService;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.util.TokenHasher;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTokenControllerTest {

    private static final String TRUSTED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = User.builder().displayName("철수").status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        userId = user.getId();
    }

    @Test
    void refresh_withValidCookieAndOrigin_returnsAccessTokenAndRotatesCookie() throws Exception {
        String oldRawToken = refreshTokenService.issue(userId);

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                        .cookie(new Cookie(RefreshTokenService.COOKIE_NAME, oldRawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(RefreshTokenService.COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString(oldRawToken))));

        AuthRefreshToken oldReloaded = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(oldRawToken))
                .orElseThrow();
        assertThat(oldReloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_withoutCookie_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withUnknownCookie_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                        .cookie(new Cookie(RefreshTokenService.COOKIE_NAME, "no-such-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withUntrustedOrigin_isRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
