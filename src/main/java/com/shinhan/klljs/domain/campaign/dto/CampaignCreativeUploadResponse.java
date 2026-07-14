package com.shinhan.klljs.domain.campaign.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 업로드 쓰기 URL과 업로드 이후 사용할 공개 읽기 URL을 함께 제공한다.
 * uploadUrl은 expiresAt까지만 유효하지만 creativeUrl은 만료되지 않는다.
 */
public record CampaignCreativeUploadResponse(
        String uploadUrl,
        String creativeUrl,
        String method,
        Map<String, String> requiredHeaders,
        String creativeToken,
        OffsetDateTime expiresAt
) {
    public CampaignCreativeUploadResponse {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
