package com.shinhan.klljs.domain.team.verification;

/**
 * 한 번의 검증이 만들어낸 산출물 묶음.
 *
 * 어떤 값을 DB에 남길지는 이 패키지가 알 바가 아니다. 정규화에 실패했을 때 원문을 저장할지
 * 버릴지는 저장 계층(BusinessRegistrationWriteService)이 컬럼 사정을 보고 결정한다.
 *
 * @param normalizedInput 정규화에 성공한 입력값. <b>형식 오류로 국세청을 호출조차 못 했으면 null</b>
 * @param classification  광고업 분류 결과. 입력 형식과 무관하게 항상 존재한다
 * @param decision        최종 판정
 */
public record BusinessVerificationResult(
        NormalizedBusinessInput normalizedInput,
        AdvertisingClassification classification,
        VerificationDecision decision
) {
}
