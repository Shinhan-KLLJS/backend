package com.shinhan.klljs.domain.team.verification;

import com.fasterxml.jackson.annotation.JsonValue;
import com.shinhan.klljs.domain.team.entity.VerificationStatus;

/**
 * 사업자등록증 검증의 최종 판정 상태 (server-verification-spec.md §4).
 *
 * DB에 저장하는 {@link VerificationStatus}(PENDING/APPROVED/REJECTED)와는 다른 개념이다.
 * 이쪽은 "이번 검증이 어떤 결론을 냈는가"이고, 저쪽은 "팀의 현재 사업자등록 상태가 무엇인가"다.
 * 둘의 매핑(§5.2)을 이 enum이 유일하게 소유해서 다른 곳에 흩어지지 않게 한다.
 */
public enum VerificationDecisionStatus {

    /** 진위 확인 통과 + 계속사업자 + 광고 관련 사업자. 세 조건을 모두 만족한 유일한 승인 경로다. */
    ACCEPTED("accepted", VerificationStatus.APPROVED),

    /** 미등록·진위실패·폐업·비광고업 중 하나. 자동으로 반려된다. */
    REJECTED("rejected", VerificationStatus.REJECTED),

    /** 입력 형식 오류나 애매한 업종처럼 자동으로 확정할 수 없는 경우. 사람이 판단해야 한다. */
    REVIEW_REQUIRED("review_required", VerificationStatus.PENDING);

    private final String code;
    private final VerificationStatus verificationStatus;

    VerificationDecisionStatus(String code, VerificationStatus verificationStatus) {
        this.code = code;
        this.verificationStatus = verificationStatus;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /** §5.2 판정 매핑. team_business_registrations.verification_status에 이 값이 저장된다. */
    public VerificationStatus toVerificationStatus() {
        return verificationStatus;
    }
}
