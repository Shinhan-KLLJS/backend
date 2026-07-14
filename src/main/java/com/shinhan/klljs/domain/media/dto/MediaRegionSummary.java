package com.shinhan.klljs.domain.media.dto;

import java.util.List;

/** 하나의 시/도와 그 아래 등록된 시/군/구 목록이다. */
public record MediaRegionSummary(String sido, List<String> sigungu) {
    public MediaRegionSummary {
        sigungu = List.copyOf(sigungu);
    }
}
