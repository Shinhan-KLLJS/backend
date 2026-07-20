-- ============================================================
-- V11__drop_campaign_image_url.sql
-- campaigns.image_url은 등록 플로우가 값을 채우는 걸 빠뜨려서 항상 NULL이었다
-- (캠페인 상세정보 조회에서 이미지가 안 보이던 버그의 원인). creative_storage_key +
-- 배포 환경별 public base URL로 그때그때 계산하도록 바꿔서 이 컬럼 자체를 없앤다 -
-- 저장된 값에 의존하면 이번처럼 "채우는 걸 깜빡하는" 버그가 다시 생길 수 있다.
-- ============================================================

ALTER TABLE campaigns
    DROP COLUMN image_url;
