package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadResponse;
import com.shinhan.klljs.domain.campaign.service.CampaignCreativeUploadService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 캠페인 등록 1단계에서 사용할 소재 업로드 URL을 발급한다. */
@RestController
@RequiredArgsConstructor
public class CampaignCreativeController implements CampaignCreativeControllerDocs {

    private final CampaignCreativeUploadService uploadService;

    @Override
    @PostMapping("/api/v1/campaign-creatives/upload-url")
    public ApiResponse<CampaignCreativeUploadResponse> issueUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CampaignCreativeUploadRequest request
    ) {
        CampaignCreativeUploadResponse response = uploadService.issue(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
