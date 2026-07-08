package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import com.shinhan.klljs.domain.auth.exception.AuthErrorCode;
import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.TokenHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC);
    private static final long TTL_SECONDS = 1209600L;

    @Autowired
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenFamilyRevoker familyRevoker;

    @Autowired
    private EntityManager entityManager;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepository, familyRevoker, FIXED_CLOCK, TTL_SECONDS);
        user = User.builder().displayName("철수").status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
    }

    @Test
    void issue_createsRowMatchingReturnedRawTokenHash() {
        String rawToken = service.issue(user.getId());
        entityManager.flush();

        AuthRefreshToken saved = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(rawToken))
                .orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getExpiresAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusSeconds(TTL_SECONDS));
    }

    @Test
    void rotate_revokesOldTokenAndIssuesNewOneInSameFamily() {
        String firstToken = service.issue(user.getId());
        AuthRefreshToken firstEntity = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(firstToken))
                .orElseThrow();
        byte[] familyId = firstEntity.getTokenFamilyId();

        String secondToken = service.rotate(firstToken);
        entityManager.flush();

        AuthRefreshToken oldReloaded = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(firstToken))
                .orElseThrow();
        assertThat(oldReloaded.getRevokedAt()).isNotNull();

        AuthRefreshToken newEntity = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(secondToken))
                .orElseThrow();
        assertThat(oldReloaded.getReplacedByTokenId()).isEqualTo(newEntity.getId());
        assertThat(newEntity.getTokenFamilyId()).isEqualTo(familyId);
        assertThat(newEntity.getRevokedAt()).isNull();
    }

    @Test
    void rotate_throwsForUnknownToken() {
        assertThatThrownBy(() -> service.rotate("no-such-token"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void rotate_throwsForExpiredToken() {
        AuthRefreshToken expired = AuthRefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256("expired-raw-token"))
                .tokenFamilyId(new byte[16])
                .expiresAt(LocalDateTime.now(FIXED_CLOCK).minusSeconds(1))
                .build();
        entityManager.persist(expired);

        assertThatThrownBy(() -> service.rotate("expired-raw-token"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void rotate_reuseOfAlreadyRevokedTokenRevokesWholeFamily() {
        String firstToken = service.issue(user.getId());
        String secondToken = service.rotate(firstToken);

        // family 전체 폐기는 REQUIRES_NEW로 별도 커밋되므로, 그게 볼 수 있도록 여기까지를 실제로 커밋한다.
        // (테스트가 @Transactional로 감싸여 있어 커밋하지 않으면 REQUIRES_NEW 트랜잭션에는 이 데이터가 안 보인다)
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // firstToken은 이미 회전으로 폐기됨 -> 다시 제시하면 탈취 의심으로 처리되어야 함
        assertThatThrownBy(() -> service.rotate(firstToken))
                .isInstanceOf(GeneralException.class);

        AuthRefreshToken secondReloaded = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(secondToken))
                .orElseThrow();
        assertThat(secondReloaded.getRevokedAt())
                .as("정상적으로 회전되어 아직 살아있어야 했던 두 번째 토큰까지 family 전체 폐기로 무효화되어야 함")
                .isNotNull();
    }

    @Test
    void revokeAllForUser_revokesOnlyStillActiveTokens() {
        String activeToken = service.issue(user.getId());
        service.rotate(activeToken); // activeToken은 이제 회전으로 이미 폐기된 상태
        String stillActiveToken = service.issue(user.getId());

        service.revokeAllForUser(user.getId());
        entityManager.flush();

        List<AuthRefreshToken> remaining = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        assertThat(remaining).isEmpty();

        AuthRefreshToken stillActiveReloaded = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHasher.sha256(stillActiveToken))
                .orElseThrow();
        assertThat(stillActiveReloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void buildCookie_setsExpectedAttributes() {
        ResponseCookie cookie = service.buildCookie("raw-token-value");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    void expireCookie_setsZeroMaxAgeWithSameAttributes() {
        ResponseCookie cookie = service.expireCookie();

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
    }

    @Test
    void revoke_revokesMatchingToken() {
        String rawToken = service.issue(user.getId());

        service.revoke(rawToken);
        entityManager.flush();

        AuthRefreshToken reloaded = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256(rawToken))
                .orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_isNoOpForUnknownToken() {
        assertThatCode(() -> service.revoke("no-such-token")).doesNotThrowAnyException();
    }
}
