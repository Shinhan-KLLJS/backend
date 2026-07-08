-- ============================================================
-- V3__auth_refresh_tokens.sql
-- 서비스 Refresh Token 저장 (18절): 원본은 HttpOnly 쿠키로만 전달되고
-- DB에는 SHA-256 해시만 저장한다. token_family_id로 회전 계보를 묶어
-- 재사용(탈취) 탐지 시 family 전체를 폐기할 수 있게 한다.
-- ============================================================

CREATE TABLE auth_refresh_tokens (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT UNSIGNED NOT NULL,
    token_hash           BINARY(32)      NOT NULL,
    token_family_id      BINARY(16)      NOT NULL,
    expires_at           DATETIME(3)     NOT NULL,
    revoked_at           DATETIME(3)     NULL,
    replaced_by_token_id BIGINT UNSIGNED NULL,
    created_at           DATETIME(3)     NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_refresh_user ON auth_refresh_tokens (user_id);
CREATE INDEX idx_refresh_family ON auth_refresh_tokens (token_family_id);
