-- ============================================================
-- V12__recreate_vision_summary_5s.sql
-- 운영 DB에서 vision_summary_5s 테이블이 실수로 DROP됐다(데이터 전체 삭제를
-- 의도했으나 DROP TABLE을 실행함). ddl-auto=validate 환경에서는 이 테이블이
-- 없으면 앱이 기동조차 되지 않으므로, 구조만 원래대로 재생성한다.
-- 데이터 복구는 별도로 진행하지 않는다(원래도 데이터를 지우려던 상황이었음).
--
-- 컬럼/제약/인덱스는 V1__init_schema.sql의 원본 정의에 V4__vision_summary_dedup_key_drop_seq.sql의
-- 변경사항(3컬럼 UNIQUE -> 2컬럼 UNIQUE, 중복 인덱스 제거)까지 반영한 "DROP 직전 최종 상태" 그대로다.
-- ============================================================

CREATE TABLE vision_summary_5s (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    media_unit_id         BIGINT UNSIGNED NOT NULL,
    campaign_id           BIGINT UNSIGNED NULL,
    device_id             VARCHAR(100)    NOT NULL,
    board_id              VARCHAR(100)    NOT NULL,
    seq                   BIGINT UNSIGNED NOT NULL,
    event_time            DATETIME(3)     NOT NULL,
    interval_sec          DECIMAL(6, 2)   NOT NULL,
    received_at           DATETIME(3)     NOT NULL,
    raw_payload           JSON            NULL,

    ots_count             INT UNSIGNED NOT NULL,
    lts_count             INT UNSIGNED NOT NULL,

    ots_male_count        INT UNSIGNED NOT NULL,
    ots_female_count      INT UNSIGNED NOT NULL,
    lts_male_count        INT UNSIGNED NOT NULL,
    lts_female_count      INT UNSIGNED NOT NULL,

    ots_male_under10      INT UNSIGNED NOT NULL,
    ots_male_10s          INT UNSIGNED NOT NULL,
    ots_male_20s          INT UNSIGNED NOT NULL,
    ots_male_30s          INT UNSIGNED NOT NULL,
    ots_male_40s          INT UNSIGNED NOT NULL,
    ots_male_50s          INT UNSIGNED NOT NULL,
    ots_male_60plus       INT UNSIGNED NOT NULL,

    ots_female_under10    INT UNSIGNED NOT NULL,
    ots_female_10s        INT UNSIGNED NOT NULL,
    ots_female_20s        INT UNSIGNED NOT NULL,
    ots_female_30s        INT UNSIGNED NOT NULL,
    ots_female_40s        INT UNSIGNED NOT NULL,
    ots_female_50s        INT UNSIGNED NOT NULL,
    ots_female_60plus     INT UNSIGNED NOT NULL,

    lts_male_under10      INT UNSIGNED NOT NULL,
    lts_male_10s          INT UNSIGNED NOT NULL,
    lts_male_20s          INT UNSIGNED NOT NULL,
    lts_male_30s          INT UNSIGNED NOT NULL,
    lts_male_40s          INT UNSIGNED NOT NULL,
    lts_male_50s          INT UNSIGNED NOT NULL,
    lts_male_60plus       INT UNSIGNED NOT NULL,

    lts_female_under10    INT UNSIGNED NOT NULL,
    lts_female_10s        INT UNSIGNED NOT NULL,
    lts_female_20s        INT UNSIGNED NOT NULL,
    lts_female_30s        INT UNSIGNED NOT NULL,
    lts_female_40s        INT UNSIGNED NOT NULL,
    lts_female_50s        INT UNSIGNED NOT NULL,
    lts_female_60plus     INT UNSIGNED NOT NULL,

    avg_dwell_sec         DECIMAL(10, 3) NOT NULL,
    dwell_sum_sec         DECIMAL(12, 3) NOT NULL,
    dwell_1_to_under_2s   INT UNSIGNED NOT NULL,
    dwell_2_to_under_3s   INT UNSIGNED NOT NULL,
    dwell_3_to_under_4s   INT UNSIGNED NOT NULL,
    dwell_4s_and_over     INT UNSIGNED NOT NULL,

    CONSTRAINT uk_vision_summary_media_time UNIQUE (media_unit_id, event_time),
    CONSTRAINT fk_vision_summary_media_unit FOREIGN KEY (media_unit_id) REFERENCES media_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vision_summary_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_vision_summary_campaign_time ON vision_summary_5s (campaign_id, event_time);
CREATE INDEX ix_vision_summary_event_time ON vision_summary_5s (event_time);
