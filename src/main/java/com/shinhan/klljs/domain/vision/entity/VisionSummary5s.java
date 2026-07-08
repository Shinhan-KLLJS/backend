package com.shinhan.klljs.domain.vision.entity;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vision AI의 5초 단위 메시지 한 건 = 한 행. 스키마 원본: docs/v2-vision-summary-schema.json
 * seq는 디바이스 재시작 시 1부터 리셋되는 보조 카운터라 idempotency 기준으로 쓸 수 없다 -
 * UNIQUE(media_unit_id, event_time) 위반은 실패가 아니라 "이미 처리됨"으로 취급하고
 * 정상 ACK한다 (저장소 계층에서 멱등 upsert로 처리).
 */
@Entity
@Table(
        name = "vision_summary_5s",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vision_summary_media_time",
                columnNames = {"media_unit_id", "event_time"}
        ),
        indexes = {
                @Index(name = "ix_vision_summary_campaign_time", columnList = "campaign_id, event_time"),
                @Index(name = "ix_vision_summary_event_time", columnList = "event_time")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisionSummary5s {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_unit_id", nullable = false)
    private MediaUnit mediaUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "board_id", nullable = false, length = 100)
    private String boardId;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "interval_sec", nullable = false, precision = 6, scale = 2)
    private BigDecimal intervalSec;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "ots_count", nullable = false)
    private Integer otsCount;

    @Column(name = "lts_count", nullable = false)
    private Integer ltsCount;

    @Column(name = "ots_male_count", nullable = false)
    private Integer otsMaleCount;

    @Column(name = "ots_female_count", nullable = false)
    private Integer otsFemaleCount;

    @Column(name = "lts_male_count", nullable = false)
    private Integer ltsMaleCount;

    @Column(name = "lts_female_count", nullable = false)
    private Integer ltsFemaleCount;

    // ── OTS 남성 연령별 ──
    @Column(name = "ots_male_under10", nullable = false)
    private Integer otsMaleUnder10;
    @Column(name = "ots_male_10s", nullable = false)
    private Integer otsMale10s;
    @Column(name = "ots_male_20s", nullable = false)
    private Integer otsMale20s;
    @Column(name = "ots_male_30s", nullable = false)
    private Integer otsMale30s;
    @Column(name = "ots_male_40s", nullable = false)
    private Integer otsMale40s;
    @Column(name = "ots_male_50s", nullable = false)
    private Integer otsMale50s;
    @Column(name = "ots_male_60plus", nullable = false)
    private Integer otsMale60plus;

    // ── OTS 여성 연령별 ──
    @Column(name = "ots_female_under10", nullable = false)
    private Integer otsFemaleUnder10;
    @Column(name = "ots_female_10s", nullable = false)
    private Integer otsFemale10s;
    @Column(name = "ots_female_20s", nullable = false)
    private Integer otsFemale20s;
    @Column(name = "ots_female_30s", nullable = false)
    private Integer otsFemale30s;
    @Column(name = "ots_female_40s", nullable = false)
    private Integer otsFemale40s;
    @Column(name = "ots_female_50s", nullable = false)
    private Integer otsFemale50s;
    @Column(name = "ots_female_60plus", nullable = false)
    private Integer otsFemale60plus;

    // ── LTS 남성 연령별 ──
    @Column(name = "lts_male_under10", nullable = false)
    private Integer ltsMaleUnder10;
    @Column(name = "lts_male_10s", nullable = false)
    private Integer ltsMale10s;
    @Column(name = "lts_male_20s", nullable = false)
    private Integer ltsMale20s;
    @Column(name = "lts_male_30s", nullable = false)
    private Integer ltsMale30s;
    @Column(name = "lts_male_40s", nullable = false)
    private Integer ltsMale40s;
    @Column(name = "lts_male_50s", nullable = false)
    private Integer ltsMale50s;
    @Column(name = "lts_male_60plus", nullable = false)
    private Integer ltsMale60plus;

    // ── LTS 여성 연령별 ──
    @Column(name = "lts_female_under10", nullable = false)
    private Integer ltsFemaleUnder10;
    @Column(name = "lts_female_10s", nullable = false)
    private Integer ltsFemale10s;
    @Column(name = "lts_female_20s", nullable = false)
    private Integer ltsFemale20s;
    @Column(name = "lts_female_30s", nullable = false)
    private Integer ltsFemale30s;
    @Column(name = "lts_female_40s", nullable = false)
    private Integer ltsFemale40s;
    @Column(name = "lts_female_50s", nullable = false)
    private Integer ltsFemale50s;
    @Column(name = "lts_female_60plus", nullable = false)
    private Integer ltsFemale60plus;

    // ── 응시시간 ──
    @Column(name = "avg_dwell_sec", nullable = false, precision = 10, scale = 3)
    private BigDecimal avgDwellSec;

    @Column(name = "dwell_sum_sec", nullable = false, precision = 12, scale = 3)
    private BigDecimal dwellSumSec;

    @Column(name = "dwell_1_to_under_2s", nullable = false)
    private Integer dwell1ToUnder2s;

    @Column(name = "dwell_2_to_under_3s", nullable = false)
    private Integer dwell2ToUnder3s;

    @Column(name = "dwell_3_to_under_4s", nullable = false)
    private Integer dwell3ToUnder4s;

    @Column(name = "dwell_4s_and_over", nullable = false)
    private Integer dwell4sAndOver;

    @Builder
    public VisionSummary5s(MediaUnit mediaUnit, Campaign campaign, String deviceId, String boardId, Long seq,
                            LocalDateTime eventTime, BigDecimal intervalSec, LocalDateTime receivedAt,
                            String rawPayload, Integer otsCount, Integer ltsCount,
                            Integer otsMaleCount, Integer otsFemaleCount, Integer ltsMaleCount, Integer ltsFemaleCount,
                            Integer otsMaleUnder10, Integer otsMale10s, Integer otsMale20s, Integer otsMale30s,
                            Integer otsMale40s, Integer otsMale50s, Integer otsMale60plus,
                            Integer otsFemaleUnder10, Integer otsFemale10s, Integer otsFemale20s, Integer otsFemale30s,
                            Integer otsFemale40s, Integer otsFemale50s, Integer otsFemale60plus,
                            Integer ltsMaleUnder10, Integer ltsMale10s, Integer ltsMale20s, Integer ltsMale30s,
                            Integer ltsMale40s, Integer ltsMale50s, Integer ltsMale60plus,
                            Integer ltsFemaleUnder10, Integer ltsFemale10s, Integer ltsFemale20s, Integer ltsFemale30s,
                            Integer ltsFemale40s, Integer ltsFemale50s, Integer ltsFemale60plus,
                            BigDecimal avgDwellSec, BigDecimal dwellSumSec,
                            Integer dwell1ToUnder2s, Integer dwell2ToUnder3s, Integer dwell3ToUnder4s, Integer dwell4sAndOver) {
        this.mediaUnit = mediaUnit;
        this.campaign = campaign;
        this.deviceId = deviceId;
        this.boardId = boardId;
        this.seq = seq;
        this.eventTime = eventTime;
        this.intervalSec = intervalSec;
        this.receivedAt = receivedAt;
        this.rawPayload = rawPayload;
        this.otsCount = otsCount;
        this.ltsCount = ltsCount;
        this.otsMaleCount = otsMaleCount;
        this.otsFemaleCount = otsFemaleCount;
        this.ltsMaleCount = ltsMaleCount;
        this.ltsFemaleCount = ltsFemaleCount;
        this.otsMaleUnder10 = otsMaleUnder10;
        this.otsMale10s = otsMale10s;
        this.otsMale20s = otsMale20s;
        this.otsMale30s = otsMale30s;
        this.otsMale40s = otsMale40s;
        this.otsMale50s = otsMale50s;
        this.otsMale60plus = otsMale60plus;
        this.otsFemaleUnder10 = otsFemaleUnder10;
        this.otsFemale10s = otsFemale10s;
        this.otsFemale20s = otsFemale20s;
        this.otsFemale30s = otsFemale30s;
        this.otsFemale40s = otsFemale40s;
        this.otsFemale50s = otsFemale50s;
        this.otsFemale60plus = otsFemale60plus;
        this.ltsMaleUnder10 = ltsMaleUnder10;
        this.ltsMale10s = ltsMale10s;
        this.ltsMale20s = ltsMale20s;
        this.ltsMale30s = ltsMale30s;
        this.ltsMale40s = ltsMale40s;
        this.ltsMale50s = ltsMale50s;
        this.ltsMale60plus = ltsMale60plus;
        this.ltsFemaleUnder10 = ltsFemaleUnder10;
        this.ltsFemale10s = ltsFemale10s;
        this.ltsFemale20s = ltsFemale20s;
        this.ltsFemale30s = ltsFemale30s;
        this.ltsFemale40s = ltsFemale40s;
        this.ltsFemale50s = ltsFemale50s;
        this.ltsFemale60plus = ltsFemale60plus;
        this.avgDwellSec = avgDwellSec;
        this.dwellSumSec = dwellSumSec;
        this.dwell1ToUnder2s = dwell1ToUnder2s;
        this.dwell2ToUnder3s = dwell2ToUnder3s;
        this.dwell3ToUnder4s = dwell3ToUnder4s;
        this.dwell4sAndOver = dwell4sAndOver;
    }
}
