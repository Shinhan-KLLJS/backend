package com.shinhan.klljs.domain.vision;

import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;

import java.math.BigDecimal;
import java.time.Instant;

/** Vision consumer와 fan-out 테스트가 확정 v2 메시지 구조를 공유하도록 만드는 테스트 픽스처다. */
public final class VisionSummaryFixtures {

    public static final String RAW_BODY = """
            {
              "device_id": "adscope-cam-01",
              "board_id": "board_gangnam_01",
              "seq": 20,
              "timestamp": "2026-07-07T02:19:20Z",
              "interval_sec": 5.425,
              "ots_count": 10,
              "lts_count": 4,
              "ots_demographics": {
                "male": {"count": 6, "age": {"under10": 0, "10s": 1, "20s": 2, "30s": 1, "40s": 1, "50s": 1, "60plus": 0}},
                "female": {"count": 4, "age": {"under10": 0, "10s": 0, "20s": 1, "30s": 1, "40s": 1, "50s": 1, "60plus": 0}}
              },
              "lts_demographics": {
                "male": {"count": 3, "age": {"under10": 0, "10s": 0, "20s": 1, "30s": 1, "40s": 1, "50s": 0, "60plus": 0}},
                "female": {"count": 1, "age": {"under10": 0, "10s": 0, "20s": 1, "30s": 0, "40s": 0, "50s": 0, "60plus": 0}}
              },
              "attention": {
                "avg_dwell_sec": 2.5,
                "dwell_sum_sec": 10.0,
                "dwell_distribution": {"1_to_under_2s": 1, "2_to_under_3s": 2, "3_to_under_4s": 1, "4s_and_over": 0}
              }
            }
            """;

    private VisionSummaryFixtures() {
    }

    public static VisionSummaryMessage message() {
        VisionSummaryMessage.AgeBuckets otsMaleAge =
                new VisionSummaryMessage.AgeBuckets(0, 1, 2, 1, 1, 1, 0);
        VisionSummaryMessage.AgeBuckets otsFemaleAge =
                new VisionSummaryMessage.AgeBuckets(0, 0, 1, 1, 1, 1, 0);
        VisionSummaryMessage.AgeBuckets ltsMaleAge =
                new VisionSummaryMessage.AgeBuckets(0, 0, 1, 1, 1, 0, 0);
        VisionSummaryMessage.AgeBuckets ltsFemaleAge =
                new VisionSummaryMessage.AgeBuckets(0, 0, 1, 0, 0, 0, 0);

        return new VisionSummaryMessage(
                "adscope-cam-01",
                "board_gangnam_01",
                20L,
                Instant.parse("2026-07-07T02:19:20Z"),
                new BigDecimal("5.425"),
                10,
                4,
                new VisionSummaryMessage.Demographics(
                        new VisionSummaryMessage.GenderDemographics(6, otsMaleAge),
                        new VisionSummaryMessage.GenderDemographics(4, otsFemaleAge)
                ),
                new VisionSummaryMessage.Demographics(
                        new VisionSummaryMessage.GenderDemographics(3, ltsMaleAge),
                        new VisionSummaryMessage.GenderDemographics(1, ltsFemaleAge)
                ),
                new VisionSummaryMessage.Attention(
                        new BigDecimal("2.5"),
                        new BigDecimal("10.0"),
                        new VisionSummaryMessage.DwellDistribution(1, 2, 1, 0)
                )
        );
    }
}
