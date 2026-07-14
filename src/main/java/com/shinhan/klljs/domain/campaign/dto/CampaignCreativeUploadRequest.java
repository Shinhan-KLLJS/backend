package com.shinhan.klljs.domain.campaign.dto;

import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 캠페인 소재 Presigned PUT URL을 발급받기 위한 요청이다. */
public record CampaignCreativeUploadRequest(
        @NotNull
        @Schema(description = "소재 유형", example = "IMAGE")
        CampaignCreativeType creativeType,

        @NotBlank @Size(max = 255)
        @Schema(description = "원본 파일명", example = "nike_summer_2026.jpg")
        String originalFilename,

        @NotBlank @Size(max = 100)
        @Schema(description = "MIME 타입", example = "image/jpeg")
        String contentType
) {
}
