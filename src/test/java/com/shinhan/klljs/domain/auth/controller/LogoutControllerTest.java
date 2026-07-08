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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogoutControllerTest {

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
    void logout_withValidCookieAndOrigin_revokesTokenAndClearsCookie() throws Exception {
        String rawToken = refreshTokenService.issue(userId);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                        .cookie(new Cookie(RefreshTokenService.COOKIE_NAME, rawToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        AuthRefreshToken reloaded = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHasher.sha256(rawToken))
                .orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_withoutCookie_isNoOpAndStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_withUntrustedOrigin_isRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
