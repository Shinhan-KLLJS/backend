package com.shinhan.klljs.domain.media.controller;

import com.shinhan.klljs.domain.media.dto.MediaRegionListResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateRequest;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitListResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/** MediaUnitController의 Swagger 계약이다. */
@Tag(name = "매체", description = "캠페인 송출 매체 등록·검색·지역 필터 API")
public interface MediaUnitControllerDocs {

    @Operation(summary = "관리자 매체 등록", description = "MVP 운영자가 인증 없이 매체 마스터 데이터를 등록합니다.")
    ResponseEntity<ApiResponse<MediaUnitCreateResponse>> create(MediaUnitCreateRequest request);

    @Operation(
            summary = "매체 목록·검색",
            description = """
                    ACTIVE 매체와 선택 기간의 캠페인 등록 가능 여부를 반환합니다.

                    ### 페이지네이션 (무한 스크롤)
                    매체 사진(photoUrl)이 고해상도 원본이라 목록 전체를 한 번에 내려주면 트래픽이 큽니다.
                    `offset`/`limit`으로 한 페이지씩 나눠 받으세요 - 예: 처음 진입 시 `limit=10`으로 호출,
                    이후 스크롤을 내릴 때마다 이전 `offset + limit`을 다음 `offset`으로 삼아 `limit=6`으로 반복 호출.
                    `hasMore=false`가 나오면 더 불러올 매체가 없다는 뜻입니다. `keyword`/`sido`/`sigungu`
                    필터가 적용된 뒤의 결과 기준으로 페이지가 나뉩니다.
                    """
    )
    ApiResponse<MediaUnitListResponse> getMediaUnits(
            @Parameter(description = "매체명 부분 검색어. 생략하면 전체 조회", example = "파르나스")
            String keyword,
            @Parameter(description = "시/도로 필터링. 생략하면 전체 조회", example = "서울특별시")
            String sido,
            @Parameter(description = "시/군/구로 필터링. 생략하면 전체 조회", example = "강남구")
            String sigungu,
            @Parameter(description = "캠페인 등록 가능 여부 조회 기간 시작일 (yyyy-MM-dd)", example = "2026-07-11")
            String executionStartDate,
            @Parameter(description = "캠페인 등록 가능 여부 조회 기간 종료일 (yyyy-MM-dd)", example = "2026-08-15")
            String executionEndDate,
            @Parameter(description = "건너뛸 개수. 생략하면 0", example = "10")
            Integer offset,
            @Parameter(description = "가져올 개수. 생략하면 10, 최대 50 (범위를 벗어나면 서버가 자동으로 clamp)", example = "6")
            Integer limit
    );

    @Operation(summary = "매체 지역 목록", description = "ACTIVE 매체에 실제 존재하는 시/도와 시/군/구를 반환합니다.")
    ApiResponse<MediaRegionListResponse> getRegions();
}
