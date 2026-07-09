package com.shinhan.klljs.domain.vision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SQS로 들어오는 v2 Vision Summary 메시지 원본 구조 (docs/v2-vision-summary-schema.json 그대로).
 * 필드 전부 @JsonProperty로 명시한다 - age/dwell_distribution 쪽 키(10s, 20s, 1_to_under_2s 등)가
 * 숫자로 시작해서 자바 필드명으로 바로 대응이 안 되고, Jackson의 snake_case 자동 변환 전략도
 * 이런 키는 못 다루기 때문이다.
 */
public record VisionSummaryMessage(
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("board_id") String boardId,
        @JsonProperty("seq") long seq,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("interval_sec") BigDecimal intervalSec,
        @JsonProperty("ots_count") int otsCount,
        @JsonProperty("lts_count") int ltsCount,
        @JsonProperty("ots_demographics") Demographics otsDemographics,
        @JsonProperty("lts_demographics") Demographics ltsDemographics,
        @JsonProperty("attention") Attention attention
) {
    public record Demographics(
            @JsonProperty("male") GenderDemographics male,
            @JsonProperty("female") GenderDemographics female
    ) {
    }

    public record GenderDemographics(
            @JsonProperty("count") int count,
            @JsonProperty("age") AgeBuckets age
    ) {
    }

    public record AgeBuckets(
            @JsonProperty("under10") int under10,
            @JsonProperty("10s") int age10s,
            @JsonProperty("20s") int age20s,
            @JsonProperty("30s") int age30s,
            @JsonProperty("40s") int age40s,
            @JsonProperty("50s") int age50s,
            @JsonProperty("60plus") int age60plus
    ) {
    }

    public record Attention(
            @JsonProperty("avg_dwell_sec") BigDecimal avgDwellSec,
            @JsonProperty("dwell_sum_sec") BigDecimal dwellSumSec,
            @JsonProperty("dwell_distribution") DwellDistribution dwellDistribution
    ) {
    }

    public record DwellDistribution(
            @JsonProperty("1_to_under_2s") int oneToUnder2s,
            @JsonProperty("2_to_under_3s") int twoToUnder3s,
            @JsonProperty("3_to_under_4s") int threeToUnder4s,
            @JsonProperty("4s_and_over") int fourSAndOver
    ) {
    }
}
