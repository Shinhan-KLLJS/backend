package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.dto.OAuth2LoginResult;
import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OAuth2LoginSuccessFacadeTest {

    @Autowired
    private OAuth2LoginSuccessFacade facade;

    @Autowired
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void complete_issuesRefreshTokenAndReturnsItsCookie() {
        User user = userRepository.save(User.builder().displayName("철수").status(UserStatus.ACTIVE).build());

        OAuth2LoginResult result = facade.complete(user.getId());

        ResponseCookie cookie = result.refreshTokenCookie();
        assertThat(cookie.getName()).isEqualTo(RefreshTokenService.COOKIE_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();

        AuthRefreshToken saved = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(cookie.getValue()))
                .orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getRevokedAt()).isNull();
    }
}
