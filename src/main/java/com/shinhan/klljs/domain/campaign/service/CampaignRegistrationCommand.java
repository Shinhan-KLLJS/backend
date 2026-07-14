package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;

import java.time.LocalDate;

/** 트랜잭션 진입 전에 정규화·검증을 끝낸 캠페인 등록 값이다. */
record CampaignRegistrationCommand(
        String campaignName,
        String brandName,
        LocalDate executionStartDate,
        LocalDate executionEndDate,
        int dailyTargetPlayCount,
        String description,
        long mediaUnitId,
        CampaignCreativeType creativeType,
        String creativeStorageKey,
        String creativeOriginalFilename
) {
}
