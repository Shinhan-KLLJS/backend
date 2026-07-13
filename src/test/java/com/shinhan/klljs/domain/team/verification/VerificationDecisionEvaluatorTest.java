package com.shinhan.klljs.domain.team.verification;

import com.shinhan.klljs.domain.team.entity.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 판정 (server-verification-spec.md §4).
 *
 * 규칙은 순서대로 검사하고 처음 일치하는 곳에서 멈춘다. 순서가 바뀌면 결과가 달라지므로,
 * "앞 규칙이 뒤 규칙을 가린다"는 사실 자체를 테스트로 고정한다.
 */
class VerificationDecisionEvaluatorTest {

    private static final AdvertisingClassification HIGH = AdvertisingClassifier.classify("광고대행", "광고물제작");
    private static final AdvertisingClassification MEDIUM = AdvertisingClassifier.classify("서비스업", "마케팅대행");
    private static final AdvertisingClassification NOT_MATCHED = AdvertisingClassifier.classify("정보통신업", "소프트웨어");
    private static final AdvertisingClassification UNKNOWN = AdvertisingClassifier.classify(null, null);

    // ──────────────── 스펙 §4 "실제 사업자등록증으로 확인한 판정 경로" 4가지 ────────────────

    @Test
    void evaluate_advertisingAgencyActiveAndValid_isAccepted() {
        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(activeAndValid(), HIGH);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.ACCEPTED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.ACCEPTED);
    }

    @Test
    void evaluate_itBusinessActiveAndValid_isRejectedAsNonAdvertising() {
        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(activeAndValid(), NOT_MATCHED);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REJECTED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.NON_ADVERTISING_BUSINESS);
    }

    /**
     * 스펙이 판정 순서의 중요성을 보여주려고 든 예시다.
     * 진위 확인을 통과했으므로 8번 규칙만 있었다면 accepted가 나왔을 것이다.
     * 4번(영업 상태)이 먼저 걸러야 한다.
     */
    @Test
    void evaluate_closedRestaurantWithValidCertificate_isRejectedAsInactiveNotAccepted() {
        BusinessCheckResult closed = BusinessCheckResult.of(
                CertificateValidity.VALID, null, "03", "폐업자", "부가가치세 일반과세자", "20240624");

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(closed, NOT_MATCHED);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REJECTED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.INACTIVE_BUSINESS);
        // 폐업일을 함께 알려줘야 사용자가 상황을 이해할 수 있다.
        assertThat(decision.message()).contains("폐업자").contains("2024-06-24");
    }

    /** 휴업자(02)도 계속사업자가 아니므로 같은 규칙에서 걸린다. */
    @Test
    void evaluate_suspendedBusiness_isRejectedAsInactive() {
        BusinessCheckResult suspended = BusinessCheckResult.of(
                CertificateValidity.VALID, null, "02", "휴업자", "부가가치세 일반과세자", null);

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(suspended, HIGH);

        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.INACTIVE_BUSINESS);
        assertThat(decision.message()).contains("휴업자").doesNotContain("폐업일");
    }

    // ──────────────── 규칙 순서 ────────────────

    /** 1번: 입력 형식이 틀리면 국세청을 호출조차 못 하므로 check가 null이다. */
    @Test
    void evaluate_nullCheck_isReviewRequiredWithInputIncomplete() {
        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(null, HIGH);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REVIEW_REQUIRED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.VALIDATION_INPUT_INCOMPLETE);
    }

    /** 2번이 3번보다 먼저다. 미등록 번호는 진위확인도 당연히 실패하지만 사유는 UNREGISTERED여야 한다. */
    @Test
    void evaluate_unregistered_takesPrecedenceOverInvalidCertificate() {
        BusinessCheckResult unregistered = BusinessCheckResult.of(
                CertificateValidity.INVALID, "확인할 수 없습니다.", "", null,
                "국세청에 등록되지 않은 사업자등록번호입니다.", null);

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(unregistered, HIGH);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REJECTED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.UNREGISTERED_BUSINESS);
    }

    /** 3번: 등록된 번호지만 세 값의 조합이 맞지 않는다. 실패 사유(valid_msg)를 메시지에 붙인다. */
    @Test
    void evaluate_invalidCertificate_isRejectedWithValidMessage() {
        BusinessCheckResult invalid = BusinessCheckResult.of(
                CertificateValidity.INVALID, "대표자명이 일치하지 않습니다.", "01", "계속사업자", "일반과세자", null);

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(invalid, HIGH);

        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.INVALID_CERTIFICATE);
        assertThat(decision.message()).contains("대표자명이 일치하지 않습니다.");
    }

    /** 5번이 6번보다 먼저다. 근거가 없는 것(unknown)과 근거가 있는데 광고업이 아닌 것(not_matched)은 다르다. */
    @Test
    void evaluate_missingBusinessTypeAndItem_isReviewRequiredNotRejected() {
        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(activeAndValid(), UNKNOWN);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REVIEW_REQUIRED);
        assertThat(decision.reasonCode())
                .isEqualTo(VerificationReasonCode.ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE);
    }

    /** 7번: 광고 관련으로는 보되, 중간 신뢰도만으로는 자동 승인하지 않는다. */
    @Test
    void evaluate_mediumConfidenceOnly_isReviewRequired() {
        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(activeAndValid(), MEDIUM);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REVIEW_REQUIRED);
        assertThat(decision.reasonCode())
                .isEqualTo(VerificationReasonCode.ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED);
    }

    /**
     * 9번: /status(상태조회)만으로 검증하면 진위가 NOT_CHECKED다.
     * 영업 중인 광고업 사업자로 확인됐어도 사업자등록증이 진짜라는 근거가 없으므로 승인하지 않는다.
     * 남의 실존 사업자번호를 넣어도 상태조회는 통과하기 때문이다.
     *
     * <h3>⚠️ 이 테스트는 삭제 방지 가드다</h3>
     * 지금 운영 경로는 이 규칙에 도달하지 않는다 (check()가 항상 /validate를 호출한다). 그래서
     * NOT_CHECKED와 규칙 9는 죽은 코드로 오해받기 쉽다.
     *
     * 이 둘은 스펙 §5.1의 <b>폐업 재검증 배치</b>를 위해 남겨둔 것이다 — 그 배치는 /status만
     * 호출하므로 진위가 확인되지 않은 상태로 판정에 들어온다.
     * <b>이 테스트가 삭제되거나 실패한다면 그 배치의 계약이 깨진 것이다</b> — 진위확인 없이
     * 승인이 유지되는 경로가 열린다. 자세한 이유는 CertificateValidity.NOT_CHECKED의 Javadoc 참고.
     */
    @Test
    void evaluate_certificateNotChecked_isReviewRequiredNotAccepted() {
        BusinessCheckResult statusOnly = BusinessCheckResult.of(
                CertificateValidity.NOT_CHECKED, null, "01", "계속사업자", "일반과세자", null);

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(statusOnly, HIGH);

        assertThat(decision.status()).isEqualTo(VerificationDecisionStatus.REVIEW_REQUIRED);
        assertThat(decision.reasonCode()).isEqualTo(VerificationReasonCode.VALIDATION_UNDETERMINED);
    }

    // ──────────────── §5.2 판정 매핑 ────────────────

    @Test
    void decisionStatus_mapsToVerificationStatusForPersistence() {
        assertThat(VerificationDecisionStatus.ACCEPTED.toVerificationStatus())
                .isEqualTo(VerificationStatus.APPROVED);
        assertThat(VerificationDecisionStatus.REJECTED.toVerificationStatus())
                .isEqualTo(VerificationStatus.REJECTED);
        assertThat(VerificationDecisionStatus.REVIEW_REQUIRED.toVerificationStatus())
                .isEqualTo(VerificationStatus.PENDING);
    }

    /** 진위 확인 통과 + 계속사업자. 광고업 분류만 바꿔가며 4번 이후 규칙을 시험할 때 쓴다. */
    private static BusinessCheckResult activeAndValid() {
        return BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "부가가치세 일반과세자", null);
    }
}
