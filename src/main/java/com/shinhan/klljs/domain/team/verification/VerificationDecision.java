package com.shinhan.klljs.domain.team.verification;

/**
 * 사업자등록증 검증의 최종 판정 (server-verification-spec.md §4).
 *
 * @param status     accepted / rejected / review_required
 * @param reasonCode 어떤 규칙에서 판정이 확정됐는지. 프론트는 이 값으로 분기한다
 * @param message    사용자에게 보여줄 문구. rejection_reason 컬럼에 그대로 저장된다
 */
public record VerificationDecision(
        VerificationDecisionStatus status,
        VerificationReasonCode reasonCode,
        String message
) {
}
