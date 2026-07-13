package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignCreativeTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T01:00:00Z");

    @Test
    void issueAndVerify_roundTripsTrustedCreativeMetadata() {
        CampaignCreativeTokenService service = serviceAt(NOW);

        CampaignCreativeTokenService.IssuedCreativeToken issued = service.issue(
                42L, CampaignCreativeType.VIDEO, "campaign-creatives/42/object-id", "summer.mp4"
        );
        CampaignCreativeTokenService.VerifiedCreative verified = service.verify(issued.token(), 42L);

        assertThat(issued.expiresAtEpochSecond()).isEqualTo(NOW.plusSeconds(3600).getEpochSecond());
        assertThat(verified.creativeType()).isEqualTo(CampaignCreativeType.VIDEO);
        assertThat(verified.s3Key()).isEqualTo("campaign-creatives/42/object-id");
        assertThat(verified.originalFilename()).isEqualTo("summer.mp4");
    }

    @Test
    void verify_rejectsTamperedSignatureAndDifferentUploader() {
        CampaignCreativeTokenService service = serviceAt(NOW);
        String token = service.issue(
                42L, CampaignCreativeType.IMAGE, "campaign-creatives/42/object-id", "poster.png"
        ).token();
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertInvalidToken(() -> service.verify(tampered, 42L));
        assertInvalidToken(() -> service.verify(token, 99L));
    }

    @Test
    void verify_rejectsExpiredTokenAndWrongStoragePrefix() {
        CampaignCreativeTokenService issuer = serviceAt(NOW);
        String expiredToken = issuer.issue(
                42L, CampaignCreativeType.IMAGE, "campaign-creatives/42/object-id", "poster.png"
        ).token();
        String wrongPrefixToken = issuer.issue(
                42L, CampaignCreativeType.IMAGE, "other-prefix/42/object-id", "poster.png"
        ).token();

        CampaignCreativeTokenService afterExpiry = serviceAt(NOW.plusSeconds(3601));
        assertInvalidToken(() -> afterExpiry.verify(expiredToken, 42L));
        assertInvalidToken(() -> issuer.verify(wrongPrefixToken, 42L));
    }

    private CampaignCreativeTokenService serviceAt(Instant instant) {
        return new CampaignCreativeTokenService(
                Clock.fixed(instant, ZoneOffset.UTC),
                properties()
        );
    }

    private CampaignCreativeProperties properties() {
        return new CampaignCreativeProperties(
                "test-bucket",
                "ap-northeast-2",
                "https://cdn.example.com/",
                "test-secret-that-is-not-the-jwt-secret",
                3600
        );
    }

    private void assertInvalidToken(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getErrorCode())
                        .isEqualTo(CampaignErrorCode.INVALID_CREATIVE_TOKEN));
    }
}
