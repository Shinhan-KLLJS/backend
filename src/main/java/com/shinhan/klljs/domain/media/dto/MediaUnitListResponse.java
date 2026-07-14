package com.shinhan.klljs.domain.media.dto;

import java.util.List;

/** 페이지네이션 없이 조건에 맞는 ACTIVE 매체 전체를 반환한다. */
public record MediaUnitListResponse(List<MediaUnitSummary> mediaUnits) {
    public MediaUnitListResponse {
        mediaUnits = List.copyOf(mediaUnits);
    }
}
