-- Campaign registration requires one creative and one media unit at creation time.
-- Environments with existing campaign rows must backfill the new creative columns before this migration.
ALTER TABLE campaigns
    ADD COLUMN creative_type VARCHAR(20) NOT NULL AFTER description,
    ADD COLUMN creative_storage_key VARCHAR(1024) NOT NULL AFTER creative_type,
    ADD COLUMN creative_original_filename VARCHAR(255) NOT NULL AFTER creative_storage_key,
    MODIFY COLUMN media_unit_id BIGINT UNSIGNED NOT NULL,
    ADD CONSTRAINT chk_campaign_creative_type CHECK (creative_type IN ('IMAGE', 'VIDEO'));

-- The admin registration API always receives structured region and coordinate values.
ALTER TABLE media_units
    MODIFY COLUMN sido VARCHAR(20) NOT NULL,
    MODIFY COLUMN sigungu VARCHAR(50) NOT NULL,
    MODIFY COLUMN latitude DECIMAL(10, 7) NOT NULL,
    MODIFY COLUMN longitude DECIMAL(10, 7) NOT NULL,
    DROP INDEX uk_media_units_board_code,
    DROP INDEX uk_media_units_device_code;

-- Multiple MVP media units intentionally share one Vision board/device pair.
CREATE INDEX ix_media_units_vision_codes_status
    ON media_units (board_code, device_code, status);
