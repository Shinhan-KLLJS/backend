-- ============================================================
-- V11__team_invite_links_plaintext_code.sql
-- MVP 단계 단순화: 초대 코드를 SHA-256 해시 대신 평문으로 저장한다.
--
-- invite_code를 NULL 허용으로 추가한다 - NOT NULL로 추가하면 이미 행이 있는 테이블에서
-- 기본값이 없어 ALTER 자체가 거부된다. 그렇다고 기존 행을 지우고 시작할 수도 없다 -
-- team_members.joined_via_invite_id가 ON DELETE RESTRICT라 초대로 합류한 이력이 있는
-- 행은 삭제가 막힌다. 해시값에서 원본 코드를 복원할 방법도 없으므로, 기존 행은
-- invite_code가 NULL인 채로 남긴다 (MySQL/InnoDB는 UNIQUE 컬럼에서 NULL을 서로 다른
-- 값으로 취급하므로 여러 행이 NULL이어도 유니크 제약에 걸리지 않는다).
--
-- 이 마이그레이션 시점에 폐기되지 않은 활성 초대 코드가 있었다면(팀당 최대 1개),
-- 그 행도 invite_code가 NULL이 되어 더 이상 그 코드로 합류할 수 없다 - 재발급하면 된다.
-- ============================================================

ALTER TABLE team_invite_links
    DROP INDEX uk_invite_token_hash;

ALTER TABLE team_invite_links
    DROP COLUMN token_hash;

ALTER TABLE team_invite_links
    ADD COLUMN invite_code VARCHAR(7) NULL AFTER created_by_user_id;

ALTER TABLE team_invite_links
    ADD CONSTRAINT uk_invite_code UNIQUE (invite_code);
