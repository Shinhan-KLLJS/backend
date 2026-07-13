package com.shinhan.klljs.domain.media.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 캠페인 등록 과정에서 매체 식별자와 상태를 검증할 때 사용하는 에러 코드다. */
@Getter
@AllArgsConstructor
public enum MediaErrorCode implements BaseErrorCode {
    MEDIA_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDIA_404_001", "매체를 찾을 수 없습니다."),
    MEDIA_UNIT_NOT_ACTIVE(HttpStatus.CONFLICT, "MEDIA_409_001", "ACTIVE 상태의 매체만 사용할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
