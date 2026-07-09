package com.shinhan.klljs.domain.vision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 성별·연령 시청 비율(demographic-view-ratio), 시간·연령별 노출도(hourly-age-exposure)
 * 응답에서 공통으로 쓰는 7개 연령대. 스펙에 정의된 값은 `10S`/`20S`처럼 숫자로 시작하는데
 * 자바 enum 상수명은 숫자로 시작할 수 없어서, 상수명은 AGE_10S처럼 짓고
 * @JsonProperty로 실제 JSON 값(10S)을 고정한다.
 */
public enum AgeGroup {
    UNDER_10("0-9세"),
    @JsonProperty("10S") AGE_10S("10-19세"),
    @JsonProperty("20S") AGE_20S("20-29세"),
    @JsonProperty("30S") AGE_30S("30-39세"),
    @JsonProperty("40S") AGE_40S("40-49세"),
    @JsonProperty("50S") AGE_50S("50-59세"),
    @JsonProperty("60_PLUS") AGE_60_PLUS("60세 이상");

    private final String label;

    AgeGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
