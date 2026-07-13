package com.shinhan.klljs.domain.team.verification;

/**
 * 사업자등록증 검증의 입력 (server-verification-spec.md §1, §3).
 *
 * <h3>왜 요청 DTO를 그대로 쓰지 않는가</h3>
 * 검증은 두 진입점에서 쓰인다 — 재제출({@code POST /teams/{teamId}/business-registration})과
 * 팀 생성({@code POST /teams}). 두 API의 요청 DTO는 서로 다르다(팀 생성은 {@code teamName}이 있고
 * {@code teamId}는 없다). 검증 코어가 어느 한쪽 DTO에 묶이면 다른 쪽에서 재사용할 수 없다.
 *
 * 그래서 검증에 <b>실제로 필요한 다섯 값만</b> 담은 이 커맨드를 두고, 각 진입점의 서비스가
 * 자기 DTO를 여기로 옮겨 담는다. 검증 코어는 누가 불렀는지 알 필요가 없다.
 *
 * <h3>정규화 전의 원문을 담는다</h3>
 * 하이픈이 섞인 사업자번호({@code 495-92-40582}), {@code "홍길동 외 1명"} 같은 공동대표 표기가
 * 그대로 들어온다. 정규화는 {@link BusinessInputNormalizer}가 하고, 실패하면 국세청을 호출하지
 * 않고 판정 규칙 1번(review_required)으로 빠진다.
 *
 * @param businessType 업태. 종목과 함께 광고업 분류의 유일한 입력이다
 * @param businessItem 종목. 업태와 함께 광고업 분류의 유일한 입력이다
 */
public record BusinessVerificationCommand(
        String businessNumber,
        String businessOpeningDate,
        String representativeName,
        String businessType,
        String businessItem
) {
}
