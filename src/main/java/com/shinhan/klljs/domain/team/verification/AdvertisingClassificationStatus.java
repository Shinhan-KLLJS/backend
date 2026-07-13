package com.shinhan.klljs.domain.team.verification;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 광고업 분류 결과 상태 (server-verification-spec.md §3.3).
 * {@link AdvertisingConfidence}와 짝을 이루며, UNKNOWN만이 "판단 근거 자체가 없다"는 뜻이다.
 */
public enum AdvertisingClassificationStatus {

    /** 키워드가 하나 이상 매칭됐다. 신뢰도는 confidence로 구분한다. */
    MATCHED("matched"),

    /** 업태·종목이 모두 비어 있어 판단할 수 없다. */
    UNKNOWN("unknown"),

    /** 업태·종목은 있으나 광고 관련 키워드가 없다. */
    NOT_MATCHED("not_matched");

    private final String code;

    AdvertisingClassificationStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
