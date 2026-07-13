package com.shinhan.klljs.domain.team.verification;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 광고업 분류의 신뢰도 (server-verification-spec.md §3.3).
 * API 응답에는 스펙에 적힌 소문자 문자열 그대로 나가야 해서 @JsonValue로 직렬화 값을 고정한다.
 */
public enum AdvertisingConfidence {

    /** 높은 신뢰도 키워드가 하나 이상 매칭. 수동 검토 없이 승인 후보가 된다. */
    HIGH("high"),

    /** 중간 신뢰도 키워드만 매칭. 광고 관련으로 보되 수동 검토 대상이다. */
    MEDIUM("medium"),

    /** 업태·종목이 모두 비어 판단 근거가 없음. */
    UNKNOWN("unknown"),

    /** 업태·종목은 있으나 어떤 키워드도 매칭되지 않음. */
    NONE("none");

    private final String code;

    AdvertisingConfidence(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
