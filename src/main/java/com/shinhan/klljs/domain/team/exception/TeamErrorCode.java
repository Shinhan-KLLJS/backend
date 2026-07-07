package com.shinhan.klljs.domain.team.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeamErrorCode implements BaseErrorCode {
    INVALID_INVITE(HttpStatus.BAD_REQUEST, "TEAM_400_001", "유효하지 않은 초대입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
