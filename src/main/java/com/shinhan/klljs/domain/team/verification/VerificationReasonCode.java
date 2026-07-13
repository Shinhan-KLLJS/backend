package com.shinhan.klljs.domain.team.verification;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 최종 판정의 사유 코드 (server-verification-spec.md §4).
 * 프론트는 code로 분기하고, message는 rejection_reason에 그대로 저장되어 사용자에게 노출된다.
 */
@Getter
@AllArgsConstructor
public enum VerificationReasonCode {

    /** 1번 규칙: 사업자번호·개업일자·대표자명 중 하나라도 형식이 어긋나 국세청 API를 호출하지 못했다. */
    VALIDATION_INPUT_INCOMPLETE("사업자등록번호, 개업일자, 대표자명을 다시 확인해 주세요."),

    /** 2번 규칙: b_stt_cd가 비어 있다. 국세청에 존재하지 않는 번호다. */
    UNREGISTERED_BUSINESS("국세청에 등록되지 않은 사업자등록번호입니다."),

    /** 3번 규칙: valid != "01". 세 값의 조합이 실제 사업자등록증과 일치하지 않는다. */
    INVALID_CERTIFICATE("사업자등록증 진위 확인에 실패했습니다."),

    /** 4번 규칙: b_stt_cd != "01". 휴업자 또는 폐업자다. */
    INACTIVE_BUSINESS("현재 운영 중인 사업자가 아닙니다."),

    /**
     * 5번 규칙: 업태와 종목이 모두 비어 광고업 여부를 판단할 수 없다.
     *
     * 이 두 값은 화면에 입력란이 없고 OCR이 읽어 서명 토큰으로만 들어오므로,
     * <b>사용자가 폼에서 고칠 수 없다.</b> 복구하려면 문서를 다시 업로드해 OCR을 재시도해야 한다.
     */
    ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE(
            "사업자등록증에서 업태·종목을 읽지 못했습니다. 더 선명한 파일로 다시 업로드해 주세요."),

    /** 6번 규칙: 업태·종목에 광고 관련 키워드가 없다. */
    NON_ADVERTISING_BUSINESS("광고 관련 사업자가 아닙니다."),

    /** 7번 규칙: "마케팅", "홍보"처럼 중간 신뢰도 키워드만 매칭됐다. 사람이 확인해야 한다. */
    ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED("광고업 여부가 명확하지 않아 검토가 필요합니다."),

    /** 8번 규칙: 모든 조건을 통과했다. */
    ACCEPTED("사업자등록증 검증을 통과했습니다."),

    /** 9번 규칙: 진위 확인을 거치지 않아 승인할 수 없다. 상세는 VerificationDecisionEvaluator 참고. */
    VALIDATION_UNDETERMINED("사업자등록증 진위 확인을 완료하지 못했습니다.");

    private final String message;
}
