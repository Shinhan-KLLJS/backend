package com.shinhan.klljs.domain.campaign.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CampaignErrorCode implements BaseErrorCode {
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMPAIGN_404_001", "캠페인을 찾을 수 없습니다."),
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CAMPAIGN_403_001", "캠페인에 접근할 권한이 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DASHBOARD_400_001", "조회 기간이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
