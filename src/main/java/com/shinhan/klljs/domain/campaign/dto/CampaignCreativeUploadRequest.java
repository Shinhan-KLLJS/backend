package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 캠페인 소재 Presigned PUT URL을 발급받기 위한 요청이다. */
public record CampaignCreativeUploadRequest(
        @NotNull CampaignCreativeType creativeType,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 100) String contentType
) {
}
