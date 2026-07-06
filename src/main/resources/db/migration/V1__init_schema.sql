-- ============================================================
-- V1__init_schema.sql
-- docs/mvp-database-erd.md 기준 초기 스키마 (prod 전용, Flyway로 관리)
-- 로컬(H2)은 Hibernate ddl-auto=create-drop을 그대로 사용하므로 이 파일을 실행하지 않는다.
-- ============================================================

-- ──────────────── users ────────────────
CREATE TABLE users (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    display_name      VARCHAR(100)  NOT NULL,
    email             VARCHAR(320)  NULL,
    profile_image_url VARCHAR(2048) NULL,
    status            VARCHAR(20)   NOT NULL,
    last_login_at     DATETIME(3)   NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_at        DATETIME(3)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── teams ────────────────
CREATE TABLE teams (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_name  VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── user_social_accounts ────────────────
CREATE TABLE user_social_accounts (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT UNSIGNED NOT NULL,
    provider          VARCHAR(20)     NOT NULL,
    provider_user_id  VARCHAR(255)    NOT NULL,
    provider_email    VARCHAR(320)    NULL,
    connected_at      DATETIME(3)     NOT NULL,
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── team_invite_links ────────────────
CREATE TABLE team_invite_links (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id            BIGINT UNSIGNED NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    token_hash         BINARY(32)      NOT NULL,
    default_role       VARCHAR(20)     NOT NULL,
    max_uses           INT UNSIGNED    NOT NULL,
    used_count         INT UNSIGNED    NOT NULL DEFAULT 0,
    expires_at         DATETIME(3)     NOT NULL,
    revoked_at         DATETIME(3)     NULL,
    created_at         DATETIME(3)     NOT NULL,
    CONSTRAINT uk_invite_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_invite_used_count_within_max CHECK (used_count <= max_uses),
    CONSTRAINT fk_invite_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invite_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── team_members ────────────────
CREATE TABLE team_members (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id               BIGINT UNSIGNED NOT NULL,
    user_id               BIGINT UNSIGNED NOT NULL,
    joined_via_invite_id  BIGINT UNSIGNED NULL,
    role                  VARCHAR(20)     NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    joined_at             DATETIME(3)     NOT NULL,
    -- 팀당 활성 OWNER 1명 강제용 generated column. 애플리케이션은 값을 절대 쓰지 않는다.
    active_owner_marker   BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN role = 'OWNER' AND status = 'ACTIVE' THEN team_id END
    ) STORED,
    CONSTRAINT uk_team_member_team_user UNIQUE (team_id, user_id),
    CONSTRAINT uk_team_members_one_active_owner UNIQUE (active_owner_marker),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_member_invite FOREIGN KEY (joined_via_invite_id) REFERENCES team_invite_links (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── team_business_registrations ────────────────
CREATE TABLE team_business_registrations (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id               BIGINT UNSIGNED NOT NULL,
    uploaded_by_user_id   BIGINT UNSIGNED NOT NULL,
    business_number       VARCHAR(20)     NULL,
    company_name          VARCHAR(200)    NULL,
    representative_name   VARCHAR(100)    NULL,
    document_storage_key  VARCHAR(1024)   NOT NULL,
    verification_status   VARCHAR(20)     NOT NULL,
    rejection_reason      VARCHAR(1000)   NULL,
    verified_at           DATETIME(3)     NULL,
    created_at            DATETIME(3)     NOT NULL,
    updated_at            DATETIME(3)     NOT NULL,
    CONSTRAINT uk_business_registration_team UNIQUE (team_id),
    CONSTRAINT fk_business_registration_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_registration_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── media_units ────────────────
CREATE TABLE media_units (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    board_code        VARCHAR(100)   NOT NULL,
    device_code       VARCHAR(100)   NOT NULL,
    media_name        VARCHAR(200)   NOT NULL,
    photo_url         VARCHAR(2048)  NULL,
    location_address  VARCHAR(500)   NOT NULL,
    latitude          DECIMAL(10, 7) NULL,
    longitude         DECIMAL(10, 7) NULL,
    width_mm          INT UNSIGNED   NULL,
    height_mm         INT UNSIGNED   NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        DATETIME(3)    NOT NULL,
    updated_at        DATETIME(3)    NOT NULL,
    CONSTRAINT uk_media_units_board_code UNIQUE (board_code),
    CONSTRAINT uk_media_units_device_code UNIQUE (device_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ──────────────── campaigns ────────────────
CREATE TABLE campaigns (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id                     BIGINT UNSIGNED NOT NULL,
    media_unit_id               BIGINT UNSIGNED NULL,
    created_by_user_id          BIGINT UNSIGNED NOT NULL,
    campaign_name               VARCHAR(200)    NOT NULL,
    execution_start_date        DATE            NOT NULL,
    execution_end_date          DATE            NOT NULL,
    daily_target_play_count     INT UNSIGNED    NOT NULL,
    description                 TEXT            NULL,
    image_url                   VARCHAR(2048)   NULL,
    status                      VARCHAR(30)     NOT NULL,
    registration_failure_reason VARCHAR(1000)   NULL,
    created_at                  DATETIME(3)     NOT NULL,
    updated_at                  DATETIME(3)     NOT NULL,
    CONSTRAINT chk_campaign_dates CHECK (execution_end_date >= execution_start_date),
    CONSTRAINT fk_campaign_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campaign_media_unit FOREIGN KEY (media_unit_id) REFERENCES media_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campaign_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_campaigns_media_unit_status ON campaigns (media_unit_id, status, execution_start_date, execution_end_date);

-- ──────────────── vision_summary_5s ────────────────
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

    CONSTRAINT uk_vision_summary_media_time_seq UNIQUE (media_unit_id, event_time, seq),
    CONSTRAINT fk_vision_summary_media_unit FOREIGN KEY (media_unit_id) REFERENCES media_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vision_summary_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_vision_summary_media_time ON vision_summary_5s (media_unit_id, event_time);
CREATE INDEX ix_vision_summary_campaign_time ON vision_summary_5s (campaign_id, event_time);
CREATE INDEX ix_vision_summary_event_time ON vision_summary_5s (event_time);
