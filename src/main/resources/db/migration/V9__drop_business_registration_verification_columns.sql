-- 진위확인·자동 판정이 MVP에서 제외되면서 승인/반려/검토필요 상태 개념 자체가 없어졌다.
-- 행이 존재한다는 것 자체가 "사용자가 사업자등록증을 제출하고 확인을 마쳤다"는 뜻이므로,
-- 검증 전용이던 세 컬럼을 내린다.
--
-- 컬럼을 남겨두는 대안(항상 같은 값 저장)은 취하지 않는다 - verification_status가 NOT NULL이라
-- 모든 INSERT가 의미 없는 값을 채워야 하고, 읽는 쪽은 "이 상태가 언제 바뀌나"를 계속 궁금해하게 된다.
-- 검증 기능이 다시 들어오면 그때의 설계에 맞는 컬럼을 새 마이그레이션으로 추가한다.
--
-- 이 테이블에는 아직 운영 데이터가 없다 (팀 생성 API가 이 스택에서 처음 열린다).

ALTER TABLE team_business_registrations
    DROP COLUMN verification_status,
    DROP COLUMN rejection_reason,
    DROP COLUMN verified_at;
