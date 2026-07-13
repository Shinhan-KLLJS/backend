package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

/** CampaignRegistrationController의 Swagger 계약이다. */
@Tag(name = "캠페인 등록", description = "캠페인 소재 업로드와 최종 등록 API")
public interface CampaignRegistrationControllerDocs {

    @Operation(
            summary = "캠페인 최종 등록",
            description = "OWNER 또는 ADMIN이 검증된 소재와 ACTIVE 매체를 사용해 캠페인을 등록합니다."
    )
    ResponseEntity<ApiResponse<CampaignRegistrationResponse>> register(
            @Parameter(hidden = true) Jwt jwt,
            Long teamId,
            CampaignRegistrationRequest request
    );
}
