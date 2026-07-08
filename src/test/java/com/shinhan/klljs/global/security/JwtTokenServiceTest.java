package com.shinhan.klljs.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JwtTokenServiceTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void generateAccessToken_producesJwtWithExpectedClaims() {
        String token = jwtTokenService.generateAccessToken(42L);

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("klljs");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
    }

    @Test
    void decode_rejectsTokenWithWrongIssuer() {
        String token = encode(claims("other-service", "access", Instant.now()));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_rejectsTokenWithWrongType() {
        String token = encode(claims("klljs", "refresh", Instant.now()));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_rejectsExpiredToken() {
        String token = encode(claims("klljs", "access", Instant.now().minusSeconds(1000)));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private JwtClaimsSet claims(String issuer, String type, Instant issuedAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .claim("typ", type)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
