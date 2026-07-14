package com.shinhan.klljs.domain.media.dto;

import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 운영 관리자가 매체 마스터 데이터를 직접 등록할 때 사용하는 요청이다. */
public record MediaUnitCreateRequest(
        @NotBlank @Size(max = 200) String mediaName,
        @NotBlank @Size(max = 2048) String photoUrl,
        @NotBlank @Size(max = 500) String locationAddress,
        @NotBlank @Size(max = 20) String sido,
        @NotBlank @Size(max = 50) String sigungu,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @Positive Integer widthMm,
        @NotNull @Positive Integer heightMm,
        @NotNull @Positive Integer resolutionWidthPx,
        @NotNull @Positive Integer resolutionHeightPx,
        @Valid @NotEmpty List<@NotNull MediaUnitShapeType> shapeTypes
) {
}
