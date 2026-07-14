package com.shinhan.klljs.domain.media.dto;

import java.util.List;

/** ACTIVE 매체 데이터에서 동적으로 계산한 지역 필터 선택지다. */
public record MediaRegionListResponse(List<MediaRegionSummary> regions) {
    public MediaRegionListResponse {
        regions = List.copyOf(regions);
    }
}
