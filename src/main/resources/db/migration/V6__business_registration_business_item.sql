-- 사업자등록증 검증(server-verification-spec.md §3)의 광고업 분류는 업태(business_type)와
-- 종목(business_item)을 이어 붙인 문자열로 판정한다. ERD에는 업태만 있어 종목을 담을 곳이 없었다.
--
-- 종목을 저장하지 않아도 accept/reject 결과 자체는 달라지지 않지만, 승인(APPROVED)된 행은
-- rejection_reason도 비어 있어 판정 근거가 DB에 하나도 남지 않는다. 사용자가 화면에서 고쳐 제출한
-- 값이 그대로 판정에 쓰이므로(§7), 원본 문서(document_storage_key)만으로는 "무엇을 제출해서
-- 승인됐는지"를 사후에 복원할 수 없다. 그래서 사용자가 확정 제출한 종목도 함께 보관한다.

ALTER TABLE team_business_registrations
    ADD COLUMN business_item VARCHAR(100) NULL AFTER business_type;
