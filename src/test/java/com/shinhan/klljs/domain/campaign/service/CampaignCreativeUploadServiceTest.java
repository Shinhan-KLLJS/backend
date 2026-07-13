package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadResponse;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignCreativeUploadServiceTest {

    @Test
    void issue_returnsWriteOnlyPresignedUrlPublicUrlAndReusableToken() {
        Instant now = Instant.parse("2026-07-13T01:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CampaignCreativeProperties properties = new CampaignCreativeProperties(
                "test-bucket",
                "ap-northeast-2",
                "https://cdn.example.com/",
                "test-secret-that-is-not-the-jwt-secret",
                3600
        );
        CampaignCreativeTokenService tokenService = new CampaignCreativeTokenService(
                clock, properties
        );

        // StaticCredentialsProvider keeps this unit test fully local; presigning never calls AWS.
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build()) {
            CampaignCreativeUploadService service = new CampaignCreativeUploadService(
                    presigner, properties, tokenService
            );

            CampaignCreativeUploadResponse response = service.issue(
                    42L, new CampaignCreativeUploadRequest(CampaignCreativeType.VIDEO, " summer.mp4 ", " video/mp4 ")
            );

            assertThat(response.method()).isEqualTo("PUT");
            assertThat(response.uploadUrl()).contains("test-bucket").contains("X-Amz-Algorithm");
            assertThat(response.creativeUrl()).startsWith("https://cdn.example.com/campaign-creatives/42/");
            assertThat(response.requiredHeaders()).containsEntry("Content-Type", "video/mp4");
            assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-07-13T11:00:00+09:00"));

            CampaignCreativeTokenService.VerifiedCreative verified = tokenService.verify(
                    response.creativeToken(), 42L
            );
            assertThat(verified.s3Key()).isEqualTo(response.creativeUrl().replace("https://cdn.example.com/", ""));
            assertThat(verified.originalFilename()).isEqualTo("summer.mp4");
        }
    }
}
