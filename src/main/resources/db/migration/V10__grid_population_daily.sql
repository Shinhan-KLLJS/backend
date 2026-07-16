-- ============================================================
-- V10__grid_population_daily.sql
-- 서울시 250m격자 생활인구(내국인) 하루 합계를 격자코드+날짜 단위로 저장한다.
-- 깔대기 그래프(6절) 전체 유동인구가 이 테이블을 조회한다.
-- ============================================================

CREATE TABLE grid_population_daily (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    grid_code           VARCHAR(20)     NOT NULL,
    population_date     DATE            NOT NULL,
    total_traffic_count BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL,
    updated_at          DATETIME(3)     NOT NULL,
    CONSTRAINT uk_grid_population_daily_code_date UNIQUE (grid_code, population_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
