package com.shinhan.klljs.global.apiPayload.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

import java.util.Objects;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public GeneralException(BaseErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
        this.errorCode = errorCode;
    }

    public GeneralException(BaseErrorCode errorCode, String detailMessage) {
        super(
                (detailMessage == null || detailMessage.isBlank())
                        ? Objects.requireNonNull(errorCode, "errorCode must not be null")
                                .getMessage()
                        : detailMessage);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
