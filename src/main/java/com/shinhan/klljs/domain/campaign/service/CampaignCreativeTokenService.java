package com.shinhan.klljs.domain.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * 업로드 단계와 최종 캠페인 등록 단계를 연결하는 짧은 수명의 HMAC 토큰을 관리한다.
 *
 * <p>토큰에는 S3 key, 업로더, 소재 유형, 원본 파일명과 만료 시각이 포함된다.
 * 최종 등록 API는 이 토큰을 검증해 클라이언트가 임의의 S3 key나 다른 사용자의 업로드를
 * 캠페인에 연결하지 못하게 한다. 토큰은 S3 객체 존재 여부 자체를 보증하지 않는다.</p>
 */
@Component
public class CampaignCreativeTokenService {

    private static final String VERSION = "v1";
    private static final String PURPOSE = "campaign-creative";
    private static final String KEY_PREFIX = "campaign-creatives/";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;
    private final CampaignCreativeProperties properties;

    public CampaignCreativeTokenService(
            Clock clock,
            CampaignCreativeProperties properties
    ) {
        // 애플리케이션 MVC/SQS ObjectMapper 설정과 무관한 고정 토큰 wire format을 사용한다.
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
        this.properties = properties;
        this.secret = properties.uploadTokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** 업로드 URL과 같은 TTL을 가진 토큰을 발급한다. */
    public IssuedCreativeToken issue(
            Long uploaderId,
            CampaignCreativeType creativeType,
            String s3Key,
            String originalFilename
    ) {
        long expiresAt = clock.instant().plus(properties.uploadTtl()).getEpochSecond();
        TokenPayload payload = new TokenPayload(
                PURPOSE, s3Key, uploaderId, creativeType, originalFilename, expiresAt
        );

        try {
            String encodedPayload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = ENCODER.encodeToString(sign(encodedPayload));
            return new IssuedCreativeToken(VERSION + "." + encodedPayload + "." + signature, expiresAt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("campaign creative token serialization failed", e);
        }
    }

    /**
     * 최종 등록 요청의 토큰을 검증하고 DB에 저장할 신뢰 가능한 소재 메타데이터를 반환한다.
     * 서명 비교는 timing attack을 피하기 위해 MessageDigest.isEqual을 사용한다.
     */
    public VerifiedCreative verify(String token, Long expectedUploaderId) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw invalidToken();
            }

            byte[] suppliedSignature = DECODER.decode(parts[2]);
            byte[] expectedSignature = sign(parts[1]);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                throw invalidToken();
            }

            TokenPayload payload = objectMapper.readValue(DECODER.decode(parts[1]), TokenPayload.class);
            validatePayload(payload, expectedUploaderId);
            return new VerifiedCreative(payload.creativeType(), payload.s3Key(), payload.originalFilename());
        } catch (GeneralException e) {
            throw e;
        } catch (IllegalArgumentException | IOException e) {
            throw invalidToken();
        }
    }

    private void validatePayload(TokenPayload payload, Long expectedUploaderId) {
        String expectedPrefix = KEY_PREFIX + expectedUploaderId + "/";
        if (!PURPOSE.equals(payload.purpose())
                || !expectedUploaderId.equals(payload.uploaderId())
                || payload.expiresAtEpochSecond() <= clock.instant().getEpochSecond()
                || payload.creativeType() == null
                || payload.originalFilename() == null
                || payload.originalFilename().isBlank()
                || payload.s3Key() == null
                || !payload.s3Key().startsWith(expectedPrefix)) {
            throw invalidToken();
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is not available", e);
        }
    }

    private GeneralException invalidToken() {
        return new GeneralException(CampaignErrorCode.INVALID_CREATIVE_TOKEN);
    }

    public record IssuedCreativeToken(String token, long expiresAtEpochSecond) {
    }

    public record VerifiedCreative(
            CampaignCreativeType creativeType,
            String s3Key,
            String originalFilename
    ) {
    }

    public record TokenPayload(
            String purpose,
            String s3Key,
            Long uploaderId,
            CampaignCreativeType creativeType,
            String originalFilename,
            long expiresAtEpochSecond
    ) {
    }
}
