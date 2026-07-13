package com.shinhan.klljs.domain.campaign.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 캠페인 소재 업로드에 필요한 환경별 설정을 한곳에 모은다.
 *
 * <p>S3 object key는 DB에 저장되지만 공개 URL의 host는 환경마다 달라질 수 있다.
 * 따라서 bucket과 public base URL을 분리해 관리하며, 토큰 서명 키는 JWT 서명 키와 공유하지 않는다.</p>
 */
@Component
public class CampaignCreativeProperties {

    private final String bucket;
    private final String region;
    private final String publicBaseUrl;
    private final String uploadTokenSecret;
    private final Duration uploadTtl;

    public CampaignCreativeProperties(
            @Value("${app.campaign.creative.bucket}") String bucket,
            @Value("${app.campaign.creative.region}") String region,
            @Value("${app.campaign.creative.public-base-url}") String publicBaseUrl,
            @Value("${app.campaign.creative.upload-token-secret}") String uploadTokenSecret,
            @Value("${app.campaign.creative.upload-ttl-seconds:3600}") long uploadTtlSeconds
    ) {
        this.bucket = requireText(bucket, "campaign creative bucket");
        this.region = requireText(region, "campaign creative region");
        this.publicBaseUrl = stripTrailingSlash(requireText(publicBaseUrl, "campaign creative public base URL"));
        this.uploadTokenSecret = requireText(uploadTokenSecret, "campaign creative upload token secret");
        if (uploadTtlSeconds <= 0) {
            throw new IllegalArgumentException("campaign creative upload TTL must be positive");
        }
        this.uploadTtl = Duration.ofSeconds(uploadTtlSeconds);
    }

    public String bucket() {
        return bucket;
    }

    public String region() {
        return region;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public String uploadTokenSecret() {
        return uploadTokenSecret;
    }

    public Duration uploadTtl() {
        return uploadTtl;
    }

    private static String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
