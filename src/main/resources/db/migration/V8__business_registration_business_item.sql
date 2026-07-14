-- 사업자등록증 OCR은 업태(business_type)와 종목(business_item)을 함께 읽는데, ERD에는 업태만 있어
-- 종목을 담을 곳이 없었다. 팀 생성 시 사용자가 화면에서 확인한 OCR 값을 그대로 저장하는 흐름이므로
-- (자동 검증·판정은 MVP 범위 밖), 문서에 찍힌 종목도 업태와 나란히 보관한다 - 원본 파일
-- (document_storage_key)만으로는 "어떤 값으로 제출됐는지"를 사후에 복원할 수 없기 때문이다.

ALTER TABLE team_business_registrations
    ADD COLUMN business_item VARCHAR(100) NULL AFTER business_type;
