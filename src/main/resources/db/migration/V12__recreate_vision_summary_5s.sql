-- ============================================================
-- V12__recreate_vision_summary_5s.sql
-- 운영 DB에서 vision_summary_5s 테이블이 실수로 DROP됐다(데이터 전체 삭제를
-- 의도했으나 DROP TABLE을 실행함). ddl-auto=validate 환경에서는 이 테이블이
-- 없으면 앱이 기동조차 되지 않으므로, 구조만 원래대로 재생성한다.
-- 데이터 복구는 별도로 진행하지 않는다(원래도 데이터를 지우려던 상황이었음).
--
-- 컬럼/제약/인덱스는 V1__init_schema.sql의 원본 정의에 V4__vision_summary_dedup_key_drop_seq.sql의
-- 변경사항(3컬럼 UNIQUE -> 2컬럼 UNIQUE, 중복 인덱스 제거)까지 반영한 "DROP 직전 최종 상태" 그대로다.
--
-- [부트스트랩 가드] 이 마이그레이션은 애초에 "테이블이 이미 DROP돼서 없는" 운영 DB만
-- 겨냥해 무조건 CREATE TABLE을 실행하도록 작성됐다. 그런데 V1부터 전체 이력을 처음부터
-- 적용하는 환경(신규 스테이징, MySQL 기반 로컬 셋업, 마이그레이션만으로 하는 DR 복구 등 -
-- 사고가 실제로 일어난 적 없는 환경)에서는 V1이 이미 이 테이블을 만들어 놓은 상태라
-- V12가 "Table 'vision_summary_5s' already exists"로 실패해 전체 마이그레이션이 막힌다.
-- 위 CREATE TABLE 정의는 V1 원본 + V4 변경사항을 반영한 스키마와 컬럼/제약/인덱스가
-- 완전히 동일함을 실제 MySQL 8.0.46으로 확인했으므로, "테이블이 없을 때만" 실행하도록
-- information_schema로 존재 여부를 확인한 뒤 PREPARE/EXECUTE로 조건부 실행한다
-- (MySQL은 CREATE INDEX에 IF NOT EXISTS를 지원하지 않아 CREATE TABLE IF NOT EXISTS만으로는
-- 부족하다 - 인덱스 생성문도 함께 가드해야 한다). 두 시나리오 모두 같은 최종 상태로 수렴한다:
--   1) 사고 복구(운영): 테이블이 없음 -> 그대로 생성한다 (기존 동작 그대로).
--   2) 신규 환경 부트스트랩: 테이블이 이미 있음(V1이 만듦) -> 아무것도 하지 않고 건너뛴다.
--
-- 주의(배포 운영자용): 운영 DB에는 이 마이그레이션이 이미 V12로 적용돼 있고
-- flyway_schema_history에 그때의 checksum이 기록돼 있다. 이 파일 내용을 바꿨으므로
-- checksum도 바뀐다 - 그대로 배포하면 다음 기동 시 Flyway validate가 checksum mismatch로
-- 실패해 앱이 뜨지 않는다. 이 PR을 운영에 배포하기 전에 운영 DB에 대해 반드시 먼저
-- `flyway repair`(또는 동등하게 flyway_schema_history의 version=12 행 checksum을 새
-- 파일 기준으로 수동 갱신)를 실행할 것. repair는 SQL을 실행하지 않고 메타데이터만
-- 갱신하므로 운영 스키마 자체에는 영향이 없다.
-- ============================================================

SET @vision_summary_5s_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'vision_summary_5s'
);

SET @ddl_create_table = IF(@vision_summary_5s_exists = 0, '
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci', 'DO 0');

PREPARE stmt FROM @ddl_create_table;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl_create_index_campaign_time = IF(@vision_summary_5s_exists = 0,
    'CREATE INDEX ix_vision_summary_campaign_time ON vision_summary_5s (campaign_id, event_time)', 'DO 0');
PREPARE stmt FROM @ddl_create_index_campaign_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl_create_index_event_time = IF(@vision_summary_5s_exists = 0,
    'CREATE INDEX ix_vision_summary_event_time ON vision_summary_5s (event_time)', 'DO 0');
PREPARE stmt FROM @ddl_create_index_event_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
