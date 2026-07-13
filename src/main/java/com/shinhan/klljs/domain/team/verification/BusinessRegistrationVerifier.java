package com.shinhan.klljs.domain.team.verification;

import com.shinhan.klljs.global.apiPayload.exception.GeneralException;

/**
 * 사업자등록의 진위와 영업 상태를 외부 기관에 조회하는 포트
 * (team-creation-api-spec.md 8절: "선택한 API는 내부 인터페이스 뒤에 감춘다").
 *
 * <b>왜 인터페이스인가</b>: 지금은 국세청(공공데이터포털 odcloud)을 쓰지만, 기관이 바뀌거나
 * 상용 대행 API로 갈아탈 수 있다. 검증 로직이 특정 벤더의 클라이언트 클래스를 직접 알고 있으면
 * 벤더를 바꿀 때 판정 규칙과 테스트까지 함께 흔들린다. 도메인은 이 포트만 알고, 벤더별 구현은
 * {@code client.nts} 같은 어댑터 패키지에 가둔다. 프론트와의 요청/응답 계약은 어느 쪽이든 그대로다.
 *
 * 구현체는 조회에 성공하면 판정에 필요한 값을 채운 {@link BusinessCheckResult}를 돌려주고,
 * 외부 API 자체가 실패하면 예외를 던진다 - "조회 실패"와 "조회 결과 유효하지 않음"은 전혀 다른 일이다.
 * 전자는 재시도할 일이고, 후자는 사용자에게 반려를 알릴 일이다.
 */
public interface BusinessRegistrationVerifier {

    /**
     * 정규화된 입력으로 진위·상태를 조회한다.
     *
     * @param input 정규화를 통과한 입력 (형식이 틀린 값은 애초에 여기까지 오지 않는다)
     * @return 판정에 필요한 값이 채워진 결과
     * @throws GeneralException 외부 API 호출 자체가 실패한 경우. DB에는 아무것도 쓰지 않는다
     */
    BusinessCheckResult check(NormalizedBusinessInput input);
}
