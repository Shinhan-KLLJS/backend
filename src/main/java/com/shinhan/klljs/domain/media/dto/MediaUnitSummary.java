package com.shinhan.klljs.domain.media.dto;

import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;

import java.math.BigDecimal;
import java.util.List;

/** 캠페인 등록 2단계의 목록과 지도에 필요한 매체 정보다. */
public record MediaUnitSummary(
        Long mediaUnitId,
        String mediaName,
        String photoUrl,
        String locationAddress,
        String sido,
        String sigungu,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer widthMm,
        Integer heightMm,
        Integer resolutionWidthPx,
        Integer resolutionHeightPx,
        List<MediaUnitShapeType> shapeTypes,
        boolean available,
        String unavailableReason
) {
    public MediaUnitSummary {
        shapeTypes = List.copyOf(shapeTypes);
    }

    public static MediaUnitSummary from(MediaUnit mediaUnit, boolean available) {
        return new MediaUnitSummary(
                mediaUnit.getId(),
                mediaUnit.getMediaName(),
                mediaUnit.getPhotoUrl(),
                mediaUnit.getLocationAddress(),
                mediaUnit.getSido(),
                mediaUnit.getSigungu(),
                mediaUnit.getLatitude(),
                mediaUnit.getLongitude(),
                mediaUnit.getWidthMm(),
                mediaUnit.getHeightMm(),
                mediaUnit.getResolutionWidthPx(),
                mediaUnit.getResolutionHeightPx(),
                mediaUnit.getShapeTypes(),
                available,
                available ? null : "PERIOD_CONFLICT"
        );
    }
}
