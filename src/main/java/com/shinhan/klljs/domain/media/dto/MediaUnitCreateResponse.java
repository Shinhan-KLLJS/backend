package com.shinhan.klljs.domain.media.dto;

import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;

/** 관리자 매체 등록 직후 식별자와 서버 고정값을 확인하는 응답이다. */
public record MediaUnitCreateResponse(
        Long mediaUnitId,
        String mediaName,
        String boardCode,
        String deviceCode,
        MediaUnitStatus status
) {
    public static MediaUnitCreateResponse from(MediaUnit mediaUnit) {
        return new MediaUnitCreateResponse(
                mediaUnit.getId(),
                mediaUnit.getMediaName(),
                mediaUnit.getBoardCode(),
                mediaUnit.getDeviceCode(),
                mediaUnit.getStatus()
        );
    }
}
