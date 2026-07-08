-- ============================================================
-- V2__campaign_brand_name_invite_code_business_fields.sql
-- V1 적용 이후 확정된 요구사항 반영:
--   1) campaigns: 브랜드명 필드 추가
--   2) team_invite_links: 코드 입력 방식 초대로 개편 (역할 선택 제거, 무제한 사용 허용,
--      팀당 활성 코드 1개 제약)
--   3) team_business_registrations: 업태/사업장 소재지 필드 추가
-- ============================================================

-- ──────────────── campaigns: 브랜드명 추가 ────────────────
ALTER TABLE campaigns
    ADD COLUMN brand_name VARCHAR(200) NOT NULL AFTER campaign_name;

-- ──────────────── team_invite_links: 초대 코드 개편 ────────────────
-- 초대 코드로 합류하면 항상 MEMBER로 합류하므로 역할 선택 컬럼 제거
ALTER TABLE team_invite_links
    DROP COLUMN default_role;

-- 팀원 수 제한이 없어 사용 횟수도 무제한을 허용해야 함 (NULL = 무제한)
ALTER TABLE team_invite_links
    MODIFY COLUMN max_uses INT UNSIGNED NULL;

ALTER TABLE team_invite_links
    DROP CHECK chk_invite_used_count_within_max;

ALTER TABLE team_invite_links
    ADD CONSTRAINT chk_invite_used_count_within_max CHECK (max_uses IS NULL OR used_count <= max_uses);

-- 팀당 폐기되지 않은(활성) 코드 1개만 허용. 새 코드 발급 시 기존 코드를 먼저 revoke해야 함
ALTER TABLE team_invite_links
    ADD COLUMN active_code_marker BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN revoked_at IS NULL THEN team_id END
    ) STORED;

ALTER TABLE team_invite_links
    ADD CONSTRAINT uk_invite_one_active_code_per_team UNIQUE (active_code_marker);

-- ──────────────── team_business_registrations: 업태/소재지 추가 ────────────────
ALTER TABLE team_business_registrations
    ADD COLUMN business_type VARCHAR(100) NULL AFTER representative_name,
    ADD COLUMN business_address VARCHAR(500) NULL AFTER business_type;
