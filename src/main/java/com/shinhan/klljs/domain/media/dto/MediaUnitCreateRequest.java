package com.shinhan.klljs.domain.media.dto;

import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @NotBlank @Size(max = 200)
        @Schema(description = "매체명", example = "파르나스 미디어타워 전광판")
        String mediaName,

        @NotBlank @Size(max = 2048)
        @Schema(description = "매체 사진 URL", example = "https://cdn.example.com/media-units/12/photo.jpg")
        String photoUrl,

        @NotBlank @Size(max = 500)
        @Schema(description = "설치 주소", example = "서울 강남구 영동대로 513")
        String locationAddress,

        @NotBlank @Size(max = 20)
        @Schema(description = "시/도", example = "서울특별시")
        String sido,

        @NotBlank @Size(max = 50)
        @Schema(description = "시/군/구", example = "강남구")
        String sigungu,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        @Schema(description = "위도", example = "37.5089")
        BigDecimal latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        @Schema(description = "경도", example = "127.0632")
        BigDecimal longitude,

        @NotNull @Positive
        @Schema(description = "화면 가로 규격(mm)", example = "81000")
        Integer widthMm,

        @NotNull @Positive
        @Schema(description = "화면 세로 규격(mm)", example = "20000")
        Integer heightMm,

        @NotNull @Positive
        @Schema(description = "해상도 가로(px)", example = "1215")
        Integer resolutionWidthPx,

        @NotNull @Positive
        @Schema(description = "해상도 세로(px)", example = "1792")
        Integer resolutionHeightPx,

        @Valid @NotEmpty
        @ArraySchema(schema = @Schema(description = "화면 형태 목록", example = "FLAT"))
        List<@NotNull MediaUnitShapeType> shapeTypes
) {
}
