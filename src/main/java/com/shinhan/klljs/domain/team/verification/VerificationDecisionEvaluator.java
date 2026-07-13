package com.shinhan.klljs.domain.team.verification;

/**
 * 진위·상태 결과와 광고업 분류 결과를 합쳐 하나의 최종 판정을 만든다 (server-verification-spec.md §4).
 *
 * <b>아래 순서대로 검사하고 처음 일치하는 규칙에서 멈춘다. 순서를 바꾸면 결과가 달라진다.</b>
 * 특히 4번(영업 상태)이 8번(진위 통과)보다 먼저 와야 한다. 진위 확인을 통과한 폐업자가 실제로
 * 존재하기 때문에, 8번만 있었다면 폐업한 음식점이 accepted로 승인됐을 것이다.
 *
 * 최종 승인 조건은 세 가지를 모두 만족하는 경우뿐이다.
 * 진위 확인 통과 + 현재 운영 중인 사업자 + 광고 관련 사업자.
 */
public final class VerificationDecisionEvaluator {

    private VerificationDecisionEvaluator() {
    }

    /**
     * @param check          국세청 조회 결과. 입력 형식 오류로 API를 호출조차 못 했으면 null
     * @param classification 광고업 분류 결과. 입력 형식과 무관하게 항상 계산된다
     */
    public static VerificationDecision evaluate(BusinessCheckResult check, AdvertisingClassification classification) {

        // 1. 검증 입력이 누락되거나 형식 오류 -> 국세청에 물어보지도 못했다.
        if (check == null) {
            return of(VerificationDecisionStatus.REVIEW_REQUIRED,
                    VerificationReasonCode.VALIDATION_INPUT_INCOMPLETE, null);
        }

        // 2. 국세청에 등록되지 않은 번호. 진위확인 결과보다 먼저 본다.
        if (!check.registered()) {
            return of(VerificationDecisionStatus.REJECTED,
                    VerificationReasonCode.UNREGISTERED_BUSINESS, null);
        }

        // 3. 진위 확인 실패. NOT_CHECKED(진위확인을 아예 안 함)는 여기 걸리지 않고 9번으로 간다.
        if (check.certificateValidity() == CertificateValidity.INVALID) {
            return of(VerificationDecisionStatus.REJECTED,
                    VerificationReasonCode.INVALID_CERTIFICATE, check.invalidMessage());
        }

        // 4. 휴업자·폐업자. 진위 확인을 통과했어도 여기서 걸러야 한다.
        if (!check.active()) {
            return of(VerificationDecisionStatus.REJECTED,
                    VerificationReasonCode.INACTIVE_BUSINESS, check.inactiveDetail());
        }

        // 5. 업태·종목이 모두 비어 광고업 여부를 판단할 근거가 없다. 6번보다 먼저 와야 반려가 아닌 검토로 빠진다.
        if (classification.isInputIncomplete()) {
            return of(VerificationDecisionStatus.REVIEW_REQUIRED,
                    VerificationReasonCode.ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE, null);
        }

        // 6. 광고 관련 사업자가 아니다.
        if (!classification.advertisingRelated()) {
            return of(VerificationDecisionStatus.REJECTED,
                    VerificationReasonCode.NON_ADVERTISING_BUSINESS, null);
        }

        // 7. "마케팅", "홍보"처럼 중간 신뢰도 키워드만 매칭됐다. 광고 관련으로는 보되 사람이 확인한다.
        if (classification.reviewRequired()) {
            return of(VerificationDecisionStatus.REVIEW_REQUIRED,
                    VerificationReasonCode.ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED, null);
        }

        // 8. 세 조건을 모두 만족했다.
        if (check.certificateValidity() == CertificateValidity.VALID) {
            return of(VerificationDecisionStatus.ACCEPTED, VerificationReasonCode.ACCEPTED, null);
        }

        // 9. 진위 확인을 거치지 않은(NOT_CHECKED) 경우만 여기까지 온다. 영업 중인 광고업 사업자로 확인됐어도
        //    사업자등록증이 진짜라는 근거가 없으므로 승인하지 않는다.
        //
        //    ⚠️ 지금은 도달하지 않는다 (check()가 항상 /validate를 호출해 VALID/INVALID만 만든다).
        //    그래도 지우지 말 것 - §5.1의 폐업 재검증 배치는 /status만 호출하고, 그때 이 규칙이 필요하다.
        //    자세한 이유는 CertificateValidity.NOT_CHECKED의 Javadoc 참고.
        return of(VerificationDecisionStatus.REVIEW_REQUIRED,
                VerificationReasonCode.VALIDATION_UNDETERMINED, null);
    }

    /** 사유 코드의 기본 문구에 상황별 부가 설명(폐업일, 진위확인 실패 사유 등)을 괄호로 덧붙인다. */
    private static VerificationDecision of(VerificationDecisionStatus status, VerificationReasonCode reasonCode,
                                            String detail) {
        String message = (detail == null || detail.isBlank())
                ? reasonCode.getMessage()
                : reasonCode.getMessage() + " (" + detail + ")";
        return new VerificationDecision(status, reasonCode, message);
    }
}
