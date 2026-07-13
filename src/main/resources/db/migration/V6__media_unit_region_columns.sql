-- 매체 지역 필터링/검색을 위해 시/도, 시/군/구를 구조화된 컬럼으로 분리해서 저장한다.
-- location_address(도로명 주소 전체)는 표시용으로 그대로 두고, 이 두 컬럼은 매체 등록/수정
-- 시 주소 검색 API 응답에서 그대로 채워 넣는다(기존 주소 문자열을 파싱해서 채우지 않는다).
-- 기존 행이 있다면 이 마이그레이션은 값을 채우지 않으므로 별도 백필이 필요하다.

ALTER TABLE media_units
    ADD COLUMN sido VARCHAR(20) NULL AFTER location_address,
    ADD COLUMN sigungu VARCHAR(50) NULL AFTER sido;

CREATE INDEX ix_media_units_region ON media_units (sido, sigungu);
