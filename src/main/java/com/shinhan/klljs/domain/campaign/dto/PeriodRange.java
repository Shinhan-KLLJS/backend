package com.shinhan.klljs.domain.campaign.dto;

import java.time.LocalDate;

/**
 * { "startDate": ..., "endDate": ... } 형태의 날짜 구간 하나.
 * executionPeriod(캠페인 집행 기간), selectedPeriod(프론트가 요청한 기간),
 * effectivePeriod(실제 집계에 쓴 기간)가 전부 이 모양을 공유해서 공용 타입으로 뺐다.
 * 캠페인 관련 API들이 대부분 이 세 가지 기간을 같이 응답하므로 여러 엔드포인트에서 재사용된다.
 */
public record PeriodRange(LocalDate startDate, LocalDate endDate) {
}
