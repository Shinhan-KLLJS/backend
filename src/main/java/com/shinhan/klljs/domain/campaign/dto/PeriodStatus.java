package com.shinhan.klljs.domain.campaign.dto;

/**
 * 선택 기간(selectedPeriod)이 캠페인 집행 기간(executionPeriod)과 비교했을 때 어디에 해당하는지.
 *
 * CampaignStatus(BEFORE_EXECUTION 등)와 값 이름이 겹치지만 완전히 다른 개념이라 일부러 분리했다:
 * - CampaignStatus: 캠페인 자체의 등록/집행 상태(DB에 저장된 값)
 * - PeriodStatus: "이번 조회 요청"의 선택 기간이 집행 기간 대비 어디쯤인지 (요청마다 매번 새로 계산됨)
 * 예를 들어 캠페인이 지금 IN_EXECUTION 상태여도, 사용자가 과거 날짜를 조회하면
 * 그 요청의 periodStatus는 BEFORE_EXECUTION이나 AFTER_EXECUTION이 될 수 있다.
 */
public enum PeriodStatus {
    // 선택 기간 전체가 집행 시작일보다 이전 (아직 집계할 데이터가 없음)
    BEFORE_EXECUTION,
    // 선택 기간이 집행 기간과 하루라도 겹침 (겹치는 부분만 effectivePeriod로 집계)
    IN_EXECUTION,
    // 선택 기간 전체가 집행 종료일보다 이후 (집행 기간 전체 데이터를 effectivePeriod로 응답)
    AFTER_EXECUTION
}
