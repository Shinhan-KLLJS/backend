-- ============================================================
-- V11__team_invite_links_plaintext_code.sql
-- MVP 단계 단순화: 초대 코드를 SHA-256 해시 대신 평문으로 저장한다.
--
-- 해시값에서 원본 코드를 복원할 방법이 없으므로, 이 마이그레이션 시점에 폐기되지 않은
-- 활성 초대 코드가 있다면 무효화된다 (관리자가 재발급하면 됨) - 팀당 활성 코드는
-- 항상 최대 1개뿐이라 영향 범위는 작다.
-- ============================================================

ALTER TABLE team_invite_links
    DROP INDEX uk_invite_token_hash;

ALTER TABLE team_invite_links
    DROP COLUMN token_hash;

ALTER TABLE team_invite_links
    ADD COLUMN invite_code VARCHAR(7) NOT NULL AFTER created_by_user_id;

ALTER TABLE team_invite_links
    ADD CONSTRAINT uk_invite_code UNIQUE (invite_code);
