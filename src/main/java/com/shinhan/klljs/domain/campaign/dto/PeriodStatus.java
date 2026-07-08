package com.shinhan.klljs.domain.campaign.dto;

/**
 * 선택 기간(selectedPeriod)과 캠페인 집행 기간(executionPeriod)의 관계.
 * CampaignStatus와 이름이 겹치는 값이 있지만 별개 개념이라 분리했다 -
 * 이건 "이번 조회가 집행 기간 대비 어디쯤인가"이고, CampaignStatus는 캠페인 자체의 등록/집행 상태다.
 */
public enum PeriodStatus {
    BEFORE_EXECUTION,
    IN_EXECUTION,
    AFTER_EXECUTION
}
