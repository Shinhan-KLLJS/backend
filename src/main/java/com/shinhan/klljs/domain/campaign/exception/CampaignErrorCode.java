package com.shinhan.klljs.domain.campaign.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 홈 대시보드 API들의 에러 코드. 스펙 8절 "에러 케이스"에 정의된 코드 문자열과 정확히 일치해야
 * 프론트가 code 값만 보고 분기할 수 있으므로, 여기 코드 값은 스펙 문서를 기준으로 한다.
 * (GeneralExceptionAdvice가 GeneralException(이 enum)을 잡아서 httpStatus/code/message를
 * 그대로 ApiResponse.onFailure로 변환해준다 - 각 API마다 예외 처리를 따로 만들 필요 없다.)
 */
@Getter
@AllArgsConstructor
public enum CampaignErrorCode implements BaseErrorCode {
    // campaign_id로 조회했는데 그런 캠페인 자체가 DB에 없을 때
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMPAIGN_404_001", "캠페인을 찾을 수 없습니다."),

    // 캠페인은 존재하지만, 요청한 사용자가 그 캠페인 소유 팀의 ACTIVE 멤버가 아닐 때
    // (다른 팀 캠페인을 남의 campaign_id로 억지로 조회하려는 경우 포함)
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CAMPAIGN_403_001", "캠페인에 접근할 권한이 없습니다."),

    // selected_start_date가 selected_end_date보다 늦은 경우처럼 요청 자체가 잘못된 경우.
    // 코드값이 CAMPAIGN_이 아니라 DASHBOARD_로 시작하는 건 스펙 원문 그대로다 -
    // 캠페인 자체의 문제가 아니라 대시보드 조회 요청 형식의 문제라서 접두사가 다르다.
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DASHBOARD_400_001", "조회 기간이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
