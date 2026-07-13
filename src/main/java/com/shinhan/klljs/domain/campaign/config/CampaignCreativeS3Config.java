package com.shinhan.klljs.domain.campaign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 캠페인 소재용 S3 Presigner를 애플리케이션 기본 AWS 자격증명 체인으로 구성한다. */
@Configuration
public class CampaignCreativeS3Config {

    @Bean(destroyMethod = "close")
    public S3Presigner campaignCreativeS3Presigner(CampaignCreativeProperties properties) {
        // 로컬은 AWS_PROFILE, 운영은 EC2 instance profile을 기본 체인이 자동으로 선택한다.
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
