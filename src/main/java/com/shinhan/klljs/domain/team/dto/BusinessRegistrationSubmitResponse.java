package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.VerificationStatus;
import com.shinhan.klljs.domain.team.verification.AdvertisingClassification;
import com.shinhan.klljs.domain.team.verification.AdvertisingClassificationStatus;
import com.shinhan.klljs.domain.team.verification.AdvertisingConfidence;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationResult;
import com.shinhan.klljs.domain.team.verification.VerificationDecision;
import com.shinhan.klljs.domain.team.verification.VerificationDecisionStatus;
import com.shinhan.klljs.domain.team.verification.VerificationReasonCode;
import com.shinhan.klljs.global.util.KstDateTimes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사업자등록증 검증 제출 응답.
 *
 * <h3>verificationStatus vs decisionStatus</h3>
 * 헷갈리기 쉬운데 서로 다른 개념이다.
 * <ul>
 *   <li>{@code verificationStatus}: 이 제출 이후 <b>팀의 현재 상태</b>. DB에 저장된 값 그대로</li>
 *   <li>{@code decisionStatus}: <b>이번 검증</b>이 내린 결론. 스펙 §4의 status</li>
 * </ul>
 * 둘의 매핑은 accepted→APPROVED, rejected→REJECTED, review_required→PENDING으로 고정이다.
 * 프론트는 분기할 때 reasonCode를 보는 편이 안전하다.
 */
public record BusinessRegistrationSubmitResponse(
        Long teamId,
        VerificationStatus verificationStatus,
        VerificationDecisionStatus decisionStatus,
        VerificationReasonCode reasonCode,
        String message,
        Advertising advertising,
        OffsetDateTime verifiedAt
) {

    /**
     * 광고업 분류 근거. 반려됐을 때 "무엇 때문에 광고업이 아니라고 봤는지" 사용자에게 보여주기 위한 값이다.
     *
     * @param matchedKeywords 매칭된 키워드 전부. 구체적인 키워드가 앞에 온다 (예: 광고대행, 광고물, 광고)
     */
    public record Advertising(
            AdvertisingClassificationStatus classificationStatus,
            AdvertisingConfidence confidence,
            List<String> matchedKeywords
    ) {
        static Advertising from(AdvertisingClassification classification) {
            return new Advertising(
                    classification.status(), classification.confidence(), classification.matchedKeywords());
        }
    }

    public static BusinessRegistrationSubmitResponse of(
            TeamBusinessRegistration registration, BusinessVerificationResult result) {

        VerificationDecision decision = result.decision();
        return new BusinessRegistrationSubmitResponse(
                registration.getTeam().getId(),
                registration.getVerificationStatus(),
                decision.status(),
                decision.reasonCode(),
                decision.message(),
                Advertising.from(result.classification()),
                // verified_at은 UTC로 저장한다. 응답에는 다른 API와 같이 "+09:00"이 붙은 KST로 내려준다.
                (registration.getVerifiedAt() == null)
                        ? null
                        : KstDateTimes.toKstOffset(registration.getVerifiedAt()));
    }
}
