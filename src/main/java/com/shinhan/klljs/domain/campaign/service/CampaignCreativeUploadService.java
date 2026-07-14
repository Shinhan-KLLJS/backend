package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** 캠페인 소재의 public-read object key와 제한된 Presigned PUT URL을 발급한다. */
@Service
@RequiredArgsConstructor
public class CampaignCreativeUploadService {

    private static final String KEY_PREFIX = "campaign-creatives/";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final S3Presigner s3Presigner;
    private final CampaignCreativeProperties properties;
    private final CampaignCreativeTokenService tokenService;

    public CampaignCreativeUploadResponse issue(Long uploaderId, CampaignCreativeUploadRequest request) {
        // 원본 파일명을 key에 넣지 않아 경로 조작과 동일 파일명 충돌을 원천적으로 피한다.
        String s3Key = KEY_PREFIX + uploaderId + "/" + UUID.randomUUID();

        CampaignCreativeTokenService.IssuedCreativeToken issuedToken = tokenService.issue(
                uploaderId, request.creativeType(), s3Key, request.originalFilename().trim()
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(s3Key)
                .contentType(request.contentType().trim())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadTtl())
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String creativeUrl = properties.publicBaseUrl() + "/" + s3Key;
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(issuedToken.expiresAtEpochSecond()), KST
        );

        return new CampaignCreativeUploadResponse(
                uploadUrl,
                creativeUrl,
                "PUT",
                Map.of("Content-Type", request.contentType().trim()),
                issuedToken.token(),
                expiresAt
        );
    }
}
