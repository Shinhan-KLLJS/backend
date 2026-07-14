package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationResponse;
import com.shinhan.klljs.domain.campaign.service.CampaignRegistrationService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 캠페인 등록 3단계의 최종 제출을 처리한다. */
@RestController
@RequiredArgsConstructor
public class CampaignRegistrationController implements CampaignRegistrationControllerDocs {

    private final CampaignRegistrationService registrationService;

    @Override
    @PostMapping("/api/v1/teams/{teamId}/campaigns")
    public ResponseEntity<ApiResponse<CampaignRegistrationResponse>> register(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @RequestBody CampaignRegistrationRequest request
    ) {
        CampaignRegistrationResponse response = registrationService.register(
                Long.valueOf(jwt.getSubject()), teamId, request
        );
        return ResponseEntity.status(GeneralSuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response));
    }
}
