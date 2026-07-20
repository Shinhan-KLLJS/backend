package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationResponse;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 캠페인 등록 요청을 검증하고 HMAC 토큰을 푼 뒤 잠금 트랜잭션에 전달하는 facade다.
 *
 * <p>이 클래스에는 @Transactional을 붙이지 않는다. S3 토큰 오류와 단순 요청 오류는 DB connection과
 * 행 잠금을 사용하기 전에 끝내고, 검증을 통과한 요청만 CampaignRegistrationTransactionService로 보낸다.</p>
 */
@Service
@RequiredArgsConstructor
public class CampaignRegistrationService {

    private final CampaignCreativeTokenService creativeTokenService;
    private final CampaignRegistrationTransactionService transactionService;

    public CampaignRegistrationResponse register(Long requesterId, Long teamId, CampaignRegistrationRequest request) {
        CampaignRegistrationCommand command = validateAndConvert(requesterId, request);
        return transactionService.register(requesterId, teamId, command);
    }

    private static final int MAX_CAMPAIGN_NAME_LENGTH = 30;
    private static final int MAX_BRAND_NAME_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 100;
    private static final int MAX_DAILY_TARGET_PLAY_COUNT = 9999;

    private CampaignRegistrationCommand validateAndConvert(Long requesterId, CampaignRegistrationRequest request) {
        if (request == null
                || isBlank(request.creativeToken())
                || isBlank(request.campaignName())
                || isBlank(request.brandName())
                || request.dailyTargetPlayCount() == null
                || request.dailyTargetPlayCount() <= 0
                || request.dailyTargetPlayCount() > MAX_DAILY_TARGET_PLAY_COUNT
                || request.mediaUnitId() == null
                || request.mediaUnitId() <= 0) {
            throw invalidRequest();
        }

        String campaignName = request.campaignName().trim();
        String brandName = request.brandName().trim();
        String description = normalizeDescription(request.description());
        if (campaignName.length() > MAX_CAMPAIGN_NAME_LENGTH
                || brandName.length() > MAX_BRAND_NAME_LENGTH
                || (description != null && description.length() > MAX_DESCRIPTION_LENGTH)) {
            throw invalidRequest();
        }

        LocalDate startDate = parseDate(request.executionStartDate());
        LocalDate endDate = parseDate(request.executionEndDate());
        if (endDate.isBefore(startDate)) {
            throw invalidRequest();
        }

        // 토큰 검증은 아래 트랜잭션 서비스 호출보다 반드시 먼저 수행한다.
        CampaignCreativeTokenService.VerifiedCreative creative = creativeTokenService.verify(
                request.creativeToken(), requesterId
        );

        return new CampaignRegistrationCommand(
                campaignName,
                brandName,
                startDate,
                endDate,
                request.dailyTargetPlayCount(),
                description,
                request.mediaUnitId(),
                creative.creativeType(),
                creative.s3Key(),
                creative.originalFilename()
        );
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            throw invalidRequest();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalidRequest();
        }
    }

    private String normalizeDescription(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private GeneralException invalidRequest() {
        return new GeneralException(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST);
    }
}
