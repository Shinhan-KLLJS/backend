package com.shinhan.klljs.domain.media.controller;

import com.shinhan.klljs.domain.media.dto.MediaRegionListResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateRequest;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateResponse;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.media.dto.MediaUnitListResponse;
import com.shinhan.klljs.domain.media.service.MediaUnitCommandService;
import com.shinhan.klljs.domain.media.service.MediaUnitQueryService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 관리자 매체 등록과 캠페인 등록 화면의 매체 조회를 제공한다. */
@RestController
@RequiredArgsConstructor
public class MediaUnitController implements MediaUnitControllerDocs {

    private final MediaUnitCommandService commandService;
    private final MediaUnitQueryService queryService;

    @Override
    @PostMapping("/api/v1/admin/media-units")
    public ResponseEntity<ApiResponse<MediaUnitCreateResponse>> create(
            @Valid @RequestBody MediaUnitCreateRequest request
    ) {
        MediaUnitCreateResponse response = commandService.create(request);
        return ResponseEntity.status(GeneralSuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response));
    }

    @Override
    @GetMapping("/api/v1/media-units")
    public ApiResponse<MediaUnitListResponse> getMediaUnits(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String executionStartDate,
            @RequestParam(required = false) String executionEndDate,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                queryService.getMediaUnits(
                        keyword, sido, sigungu, parseDate(executionStartDate), parseDate(executionEndDate),
                        offset, limit
                )
        );
    }

    @Override
    @GetMapping("/api/v1/media-units/regions")
    public ApiResponse<MediaRegionListResponse> getRegions() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, queryService.getRegions());
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new GeneralException(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST);
        }
    }
}
