package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

/** CampaignCreativeController의 Swagger 계약이다. */
@Tag(name = "캠페인 등록", description = "캠페인 소재 업로드와 최종 등록 API")
public interface CampaignCreativeControllerDocs {

    @Operation(
            summary = "캠페인 소재 업로드 URL 발급",
            description = "이미지 또는 영상용 Presigned PUT URL, 공개 조회 URL, 최종 등록용 서명 토큰을 발급합니다."
    )
    ApiResponse<CampaignCreativeUploadResponse> issueUploadUrl(
            @Parameter(hidden = true) Jwt jwt,
            CampaignCreativeUploadRequest request
    );
}
