package com.shinhan.klljs.domain.team.verification;

import java.time.LocalDate;

/**
 * 국세청 진위확인에 그대로 보낼 수 있도록 정규화가 끝난 입력값 (server-verification-spec.md §1).
 *
 * 이 객체가 만들어졌다는 것은 세 필드가 모두 형식 검사를 통과했다는 뜻이다.
 * 형식이 맞지 않으면 {@link BusinessInputNormalizer}가 아예 이 객체를 만들지 않고,
 * data.go.kr을 호출하지 않은 채 review_required로 판정된다.
 *
 * @param businessNumber     숫자 10자리. 국세청 요청의 {@code b_no}
 * @param openingDateText    {@code YYYYMMDD} 8자리. 국세청 요청의 {@code start_dt}
 * @param openingDate        위 값을 파싱한 날짜. DB의 {@code business_opening_date}에 저장한다
 * @param representativeName 공백과 말미의 {@code 외N명}을 제거한 대표자명. 국세청 요청의 {@code p_nm}
 */
public record NormalizedBusinessInput(
        String businessNumber,
        String openingDateText,
        LocalDate openingDate,
        String representativeName
) {
}
