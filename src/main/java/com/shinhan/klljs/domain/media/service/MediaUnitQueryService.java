package com.shinhan.klljs.domain.media.service;

import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.dto.MediaRegionListResponse;
import com.shinhan.klljs.domain.media.dto.MediaRegionSummary;
import com.shinhan.klljs.domain.media.dto.MediaUnitListResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitSummary;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.global.apiPayload.code.GeneralErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 캠페인 등록 화면의 매체 검색, 지역 필터와 기간 가용성을 계산한다. */
@Service
@RequiredArgsConstructor
public class MediaUnitQueryService {

    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final MediaUnitRepository mediaUnitRepository;
    private final CampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public MediaUnitListResponse getMediaUnits(
            String keyword,
            String sido,
            String sigungu,
            LocalDate executionStartDate,
            LocalDate executionEndDate,
            Integer offsetParam,
            Integer limitParam
    ) {
        validatePeriod(executionStartDate, executionEndDate);

        String normalizedKeyword = optionalText(keyword);
        String normalizedSido = optionalText(sido);
        String normalizedSigungu = optionalText(sigungu);
        if (normalizedKeyword != null && normalizedKeyword.length() > 100) {
            throw validationError("keyword는 100자를 초과할 수 없습니다.");
        }
        if (normalizedSigungu != null && normalizedSido == null) {
            throw validationError("sigungu를 지정하려면 sido도 함께 지정해야 합니다.");
        }

        // MVP의 매체 수가 작으므로 전체 ACTIVE 목록을 정렬 조회한 뒤 메모리에서 조건을 결합한다.
        // 이렇게 하면 사용자 입력의 %, _ 문자가 SQL LIKE wildcard로 해석되지 않는다.
        List<MediaUnit> filtered = mediaUnitRepository
                .findAllByStatusOrderByMediaNameAscIdAsc(MediaUnitStatus.ACTIVE)
                .stream()
                .filter(media -> matchesKeyword(media, normalizedKeyword))
                .filter(media -> normalizedSido == null || normalizedSido.equals(media.getSido()))
                .filter(media -> normalizedSigungu == null || normalizedSigungu.equals(media.getSigungu()))
                .toList();

        if (filtered.isEmpty()) {
            return new MediaUnitListResponse(List.of(), false);
        }

        // 매체 사진(photoUrl)이 고해상도 원본이라 한 번에 다 내려주면 트래픽이 크다 - 화면에 보일
        // 만큼만 페이지로 잘라서 응답한다(무한 스크롤). 매체 수가 많아져도 findConflictingMediaUnitIds가
        // 이 페이지의 ID만 조회하도록, 페이지네이션을 먼저 적용한 뒤 기간 충돌을 계산한다.
        int offset = clampOffset(offsetParam);
        int limit = clampLimit(limitParam);
        List<MediaUnit> page = filtered.stream().skip(offset).limit(limit).toList();
        boolean hasMore = offset + page.size() < filtered.size();

        if (page.isEmpty()) {
            return new MediaUnitListResponse(List.of(), false);
        }

        List<Long> pageMediaUnitIds = page.stream().map(MediaUnit::getId).toList();
        Set<Long> conflictingIds = new HashSet<>(campaignRepository.findConflictingMediaUnitIds(
                pageMediaUnitIds,
                CampaignStatus.REGISTRATION_FAILED,
                executionStartDate,
                executionEndDate
        ));

        List<MediaUnitSummary> summaries = page.stream()
                .map(media -> MediaUnitSummary.from(media, !conflictingIds.contains(media.getId())))
                .toList();
        return new MediaUnitListResponse(summaries, hasMore);
    }

    private int clampOffset(Integer offsetParam) {
        if (offsetParam == null) {
            return DEFAULT_OFFSET;
        }
        return Math.max(0, offsetParam);
    }

    private int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limitParam, MAX_LIMIT));
    }

    @Transactional(readOnly = true)
    public MediaRegionListResponse getRegions() {
        Map<String, Set<String>> regions = new TreeMap<>();
        for (MediaUnit media : mediaUnitRepository.findAllByStatusOrderByMediaNameAscIdAsc(MediaUnitStatus.ACTIVE)) {
            regions.computeIfAbsent(media.getSido(), ignored -> new TreeSet<>()).add(media.getSigungu());
        }

        List<MediaRegionSummary> result = regions.entrySet().stream()
                .map(entry -> new MediaRegionSummary(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new MediaRegionListResponse(result);
    }

    private boolean matchesKeyword(MediaUnit media, String keyword) {
        if (keyword == null) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return media.getMediaName().toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || media.getLocationAddress().toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new GeneralException(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST);
        }
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private GeneralException validationError(String detail) {
        return new GeneralException(GeneralErrorCode.VALIDATION_ERROR, detail);
    }
}
