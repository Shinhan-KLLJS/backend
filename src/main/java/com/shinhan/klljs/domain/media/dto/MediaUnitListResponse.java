package com.shinhan.klljs.domain.media.dto;

import java.util.List;

/**
 * 조건에 맞는 ACTIVE 매체를 offset/limit 기반으로 한 페이지씩 반환한다.
 * 매체 사진(photoUrl)이 고해상도 원본이라 한 번에 다 불러오면 트래픽이 커서, 목록을 무한 스크롤로
 * 나눠 받도록 페이지네이션을 추가했다 - 이미지 자체를 리사이즈하는 게 아니라 화면에 실제로 보여줄
 * 만큼만 우선 불러오는 방식이다.
 */
public record MediaUnitListResponse(List<MediaUnitSummary> mediaUnits, boolean hasMore) {
    public MediaUnitListResponse {
        mediaUnits = List.copyOf(mediaUnits);
    }
}
