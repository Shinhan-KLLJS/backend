package com.shinhan.klljs.domain.team.exception;

import com.shinhan.klljs.domain.team.verification.VerificationReasonCode;
import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 팀 생성이 검증에 걸려 실패했을 때의 에러 코드.
 *
 * <h3>왜 팀 생성만 400이고 재제출은 200인가</h3>
 * 재제출은 이미 있는 행의 재평가 결과를 <b>표현</b>하는 것이라 200 + result가 맞다. 반려 사유와
 * 광고업 매칭 근거를 화면에 보여줘야 하는데, 우리 ApiResponse는 실패 응답에 result를 실을 수 없어
 * 400으로 만들면 그 정보를 전부 잃는다.
 *
 * 팀 생성은 다르다. <b>리소스 생성 실패</b>이고 아무 row도 만들지 않으므로 400이 맞다.
 *
 * <h3>사유마다 코드를 따로 두는 이유</h3>
 * 프론트가 안내 문구를 사유별로 다르게 띄워야 하는데, 실패 응답에는 구조화된 result를 실을 수 없다.
 * 이 프로젝트의 다른 API들처럼 {@code code}로 분기하게 한다. {@link VerificationReasonCode}와
 * 상수 이름을 1:1로 맞춰, 재제출 API의 {@code result.reasonCode}와 같은 어휘를 쓰도록 했다.
 */
@Getter
@AllArgsConstructor
public enum BusinessVerificationRejectionCode implements BaseErrorCode {

    VALIDATION_INPUT_INCOMPLETE(HttpStatus.BAD_REQUEST, "BUSINESS_400_101",
            "사업자등록번호, 개업일자, 대표자명을 다시 확인해 주세요."),

    UNREGISTERED_BUSINESS(HttpStatus.BAD_REQUEST, "BUSINESS_400_102",
            "국세청에 등록되지 않은 사업자등록번호입니다."),

    INVALID_CERTIFICATE(HttpStatus.BAD_REQUEST, "BUSINESS_400_103",
            "사업자등록증 진위 확인에 실패했습니다."),

    INACTIVE_BUSINESS(HttpStatus.BAD_REQUEST, "BUSINESS_400_104",
            "현재 운영 중인 사업자가 아닙니다."),

    /**
     * OCR이 업태·종목을 못 읽었다. <b>사용자가 폼에서 고칠 수 없는 유일한 경우다</b> -
     * 이 두 값은 화면에 입력란이 없고 서명 토큰으로만 들어오기 때문이다. 재업로드를 안내한다.
     */
    ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE(HttpStatus.BAD_REQUEST, "BUSINESS_400_105",
            "사업자등록증에서 업태·종목을 읽지 못했습니다. 더 선명한 파일로 다시 업로드해 주세요."),

    NON_ADVERTISING_BUSINESS(HttpStatus.BAD_REQUEST, "BUSINESS_400_106",
            "광고 관련 사업자가 아닙니다."),

    /** 진짜 광고대행사인데 OCR 종목이 "마케팅"만 잡힌 경우도 여기 걸린다. 재업로드나 문의로 푼다. */
    ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED(HttpStatus.BAD_REQUEST, "BUSINESS_400_107",
            "광고업 여부가 명확하지 않아 검토가 필요합니다. 고객센터로 문의해 주세요."),

    VALIDATION_UNDETERMINED(HttpStatus.BAD_REQUEST, "BUSINESS_400_108",
            "사업자등록증 진위 확인을 완료하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    /**
     * <b>exhaustive switch를 쓴다.</b> VerificationReasonCode에 사유를 추가하고 여기 매핑을 빠뜨리면
     * 컴파일이 실패한다 - 새 반려 사유가 조용히 500으로 나가는 것을 막는다.
     *
     * @throws IllegalArgumentException ACCEPTED로 호출한 경우. 승인은 반려가 아니므로 호출부의 버그다
     */
    public static BusinessVerificationRejectionCode from(VerificationReasonCode reasonCode) {
        return switch (reasonCode) {
            case VALIDATION_INPUT_INCOMPLETE -> VALIDATION_INPUT_INCOMPLETE;
            case UNREGISTERED_BUSINESS -> UNREGISTERED_BUSINESS;
            case INVALID_CERTIFICATE -> INVALID_CERTIFICATE;
            case INACTIVE_BUSINESS -> INACTIVE_BUSINESS;
            case ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE -> ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE;
            case NON_ADVERTISING_BUSINESS -> NON_ADVERTISING_BUSINESS;
            case ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED -> ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED;
            case VALIDATION_UNDETERMINED -> VALIDATION_UNDETERMINED;
            case ACCEPTED -> throw new IllegalArgumentException(
                    "ACCEPTED는 반려 사유가 아니다. 승인된 검증 결과로 이 메서드를 부르면 안 된다.");
        };
    }
}
