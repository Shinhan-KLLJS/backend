-- 매체 등록 화면에서 받는 규격/해상도/형태와 사업자등록증 개업일을 저장한다.
-- 기존 데이터가 없는 상태를 전제로, 매체 등록 필수 입력값은 NOT NULL로 맞춘다.

ALTER TABLE media_units
    MODIFY COLUMN photo_url VARCHAR(2048) NOT NULL,
    MODIFY COLUMN width_mm INT UNSIGNED NOT NULL,
    MODIFY COLUMN height_mm INT UNSIGNED NOT NULL,
    ADD COLUMN resolution_width_px INT UNSIGNED NOT NULL AFTER height_mm,
    ADD COLUMN resolution_height_px INT UNSIGNED NOT NULL AFTER resolution_width_px,
    ADD COLUMN shape_types JSON NOT NULL AFTER resolution_height_px,
    ADD CONSTRAINT chk_media_units_width_positive CHECK (width_mm > 0),
    ADD CONSTRAINT chk_media_units_height_positive CHECK (height_mm > 0),
    ADD CONSTRAINT chk_media_units_resolution_width_positive CHECK (resolution_width_px > 0),
    ADD CONSTRAINT chk_media_units_resolution_height_positive CHECK (resolution_height_px > 0),
    ADD CONSTRAINT chk_media_units_shape_types_array CHECK (JSON_TYPE(shape_types) = 'ARRAY');

ALTER TABLE team_business_registrations
    ADD COLUMN business_opening_date DATE NULL AFTER business_address;