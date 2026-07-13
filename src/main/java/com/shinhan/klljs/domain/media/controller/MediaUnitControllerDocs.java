package com.shinhan.klljs.domain.media.controller;

import com.shinhan.klljs.domain.media.dto.MediaRegionListResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateRequest;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitListResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/** MediaUnitController의 Swagger 계약이다. */
@Tag(name = "매체", description = "캠페인 송출 매체 등록·검색·지역 필터 API")
public interface MediaUnitControllerDocs {

    @Operation(summary = "관리자 매체 등록", description = "MVP 운영자가 인증 없이 매체 마스터 데이터를 등록합니다.")
    ResponseEntity<ApiResponse<MediaUnitCreateResponse>> create(MediaUnitCreateRequest request);

    @Operation(summary = "매체 목록·검색", description = "ACTIVE 매체와 선택 기간의 캠페인 등록 가능 여부를 반환합니다.")
    ApiResponse<MediaUnitListResponse> getMediaUnits(
            String keyword,
            String sido,
            String sigungu,
            String executionStartDate,
            String executionEndDate
    );

    @Operation(summary = "매체 지역 목록", description = "ACTIVE 매체에 실제 존재하는 시/도와 시/군/구를 반환합니다.")
    ApiResponse<MediaRegionListResponse> getRegions();
}
