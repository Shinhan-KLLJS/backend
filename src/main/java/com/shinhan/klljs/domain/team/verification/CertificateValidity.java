package com.shinhan.klljs.domain.team.verification;

/**
 * 사업자등록증 진위 확인 결과 (server-verification-spec.md §2.1).
 *
 * boolean이 아니라 3상태인 이유는 NOT_CHECKED 때문이다. {@code /status}(상태조회)만으로는
 * 등록 여부와 영업 상태만 알 수 있을 뿐 진위는 확인되지 않는다 - 남의 실존 사업자번호를 넣어도
 * 통과하기 때문이다. 이 경우를 INVALID로 뭉뚱그리면 "진위 확인에 실패했다"는 잘못된 사유가 나가고,
 * VALID로 뭉뚱그리면 확인도 하지 않은 사업자를 승인하게 된다.
 *
 * 그래서 NOT_CHECKED는 최종 판정에서 승인도 반려도 아닌 review_required(VALIDATION_UNDETERMINED)로 빠진다.
 */
public enum CertificateValidity {

    /** {@code /validate} 응답의 {@code valid == "01"}. 세 값의 조합이 실제 사업자등록증과 일치한다. */
    VALID,

    /** {@code /validate}를 호출했으나 {@code valid != "01"}. 사유는 {@code valid_msg}에 담긴다. */
    INVALID,

    /**
     * 진위 확인을 아예 거치지 않았다. 절대 승인해서는 안 된다.
     *
     * <h3>⚠️ 지금은 도달하지 않는다. 그래도 지우지 말 것</h3>
     * {@code NtsBusinessmanClient.check()}가 항상 {@code /validate}를 호출하므로, 현재 운영
     * 경로에서 만들어지는 값은 VALID 아니면 INVALID뿐이다. 그래서 이 상수는 쓰이지 않는
     * 죽은 코드처럼 보인다.
     *
     * <b>지우면 안 되는 이유:</b> 스펙 §5.1이 {@code business_opening_date}를 저장하는 근거로 든
     * "승인된 사업자의 폐업 여부 재검증"은 {@code /status}만 호출한다 (진위확인에 필요한 대표자명·
     * 개업일을 사용자에게 다시 물을 수 없으니 배치는 상태조회만 쓴다). 그때 진위는 확인되지 않은
     * 상태이므로 이 값이 필요하다. 이걸 지우고 VALID로 뭉뚱그리면 <b>재검증 배치가 진위확인 없이
     * 승인을 유지하는 경로</b>가 되고, INVALID로 뭉뚱그리면 멀쩡한 사업자에게 "진위확인 실패"라는
     * 잘못된 사유가 나간다.
     *
     * {@code VerificationDecisionEvaluatorTest}가 이 경로를 고정하고 있다 - 그 테스트가 삭제되거나
     * 실패하면 재검증 배치의 계약이 깨진 것이다.
     */
    NOT_CHECKED
}
