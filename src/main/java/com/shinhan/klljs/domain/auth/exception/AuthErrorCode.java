package com.shinhan.klljs.domain.auth.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_001", "유효하지 않은 Refresh Token입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
