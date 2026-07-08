package com.shinhan.klljs.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 서비스 Access Token(JWT) 발급. 팀 역할은 담지 않는다 —
 * 팀 권한은 매 요청마다 team_members에서 확인한다.
 */
@Service
public class JwtTokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.auth.jwt.issuer:klljs}") String issuer,
            @Value("${app.auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String generateAccessToken(Long userId) {
        Instant now = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
