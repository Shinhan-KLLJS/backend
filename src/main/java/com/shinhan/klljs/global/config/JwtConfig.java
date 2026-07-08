package com.shinhan.klljs.global.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 서비스 Access Token(JWT) 서명·검증용 빈.
 * HMAC 대칭키를 사용한다 — 발급자와 검증자가 같은 서버이기 때문에 가능한 선택이다.
 * 운영에서 여러 서비스가 검증만 해야 하는 상황이 오면 RSA/ECDSA 비대칭키로 분리한다.
 */
@Configuration
public class JwtConfig {

    private static final String ACCESS_TOKEN_TYPE = "access";

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${app.auth.jwt.secret}") String secret
    ) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer:klljs}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                accessTokenClaimsValidator(issuer)
        );
        decoder.setJwtValidator(validator);

        return decoder;
    }

    private SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * 같은 서명 키로 다른 용도의 토큰이 나중에 생기더라도 Access Token으로 오용되지 않도록
     * issuer와 typ=access를 함께 확인한다. 서명·만료는 위 JwtValidators.createDefault()가 담당한다.
     */
    private OAuth2TokenValidator<Jwt> accessTokenClaimsValidator(String issuer) {
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "issuer 또는 typ 클레임이 유효하지 않습니다.",
                null
        );

        return jwt -> {
            boolean issuerMatches = issuer.equals(jwt.getClaimAsString("iss"));
            boolean typeMatches = ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString("typ"));

            return (issuerMatches && typeMatches)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(error);
        };
    }
}
