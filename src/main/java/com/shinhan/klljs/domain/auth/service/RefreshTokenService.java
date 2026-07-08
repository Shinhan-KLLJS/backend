package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import com.shinhan.klljs.domain.auth.exception.AuthErrorCode;
import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 서비스 Refresh Token 발급·회전·폐기.
 * 원본은 이 서비스를 호출하는 쪽(성공 Handler 등)이 HttpOnly 쿠키로 응답에 담고,
 * DB에는 SHA-256 해시만 저장한다.
 */
@Service
public class RefreshTokenService {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final int RAW_TOKEN_BYTES = 32;
    private static final int FAMILY_ID_BYTES = 16;

    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final long refreshTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            AuthRefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            Clock clock,
            @Value("${app.auth.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Transactional
    // 첫 로그인 시 호출. 새 token_family_id(16바이트 난수)로 첫 토큰을 만듭니다.
    public String issue(Long userId) {
        return createAndSave(userId, randomBytes(FAMILY_ID_BYTES)).rawToken();
    }

    /** 재사용(이미 폐기된 토큰 재제시)이 감지되면 family 전체를 폐기하고 예외를 던진다. */
    @Transactional
    public String rotate(String rawToken) {
        byte[] tokenHash = TokenHasher.sha256(rawToken);
        AuthRefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now(clock);

        if (current.getRevokedAt() != null) {
            revokeFamily(current.getTokenFamilyId(), now);
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!current.isUsable(now)) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RawTokenAndEntity next = createAndSave(current.getUser().getId(), current.getTokenFamilyId());
        current.markRotated(next.entity().getId(), now);

        return next.rawToken();
    }

    @Transactional
    // 정지/탈퇴 시 그 사용자의 살아있는 Refresh Token을 전부 무효화
    public void revokeAllForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> token.revoke(now));
    }

    public ResponseCookie buildCookie(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(refreshTokenTtlSeconds))
                .build();
    }

    private void revokeFamily(byte[] familyId, LocalDateTime now) {
        refreshTokenRepository.findByTokenFamilyId(familyId).stream()
                .filter(token -> token.getRevokedAt() == null)
                .forEach(token -> token.revoke(now));
    }

    private RawTokenAndEntity createAndSave(Long userId, byte[] familyId) {
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now(clock);

        AuthRefreshToken token = AuthRefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(TokenHasher.sha256(rawToken))
                .tokenFamilyId(familyId)
                .expiresAt(now.plusSeconds(refreshTokenTtlSeconds))
                .build();

        return new RawTokenAndEntity(rawToken, refreshTokenRepository.save(token));
    }

    private String generateRawToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(RAW_TOKEN_BYTES));
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private record RawTokenAndEntity(String rawToken, AuthRefreshToken entity) {
    }
}
