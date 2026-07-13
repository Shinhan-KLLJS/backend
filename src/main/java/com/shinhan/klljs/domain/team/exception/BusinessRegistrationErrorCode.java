package com.shinhan.klljs.domain.team.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 사업자등록증 업로드·팀 생성 API의 에러 코드.
 *
 * 코드 문자열(BUSINESS_xxx_yyy)은 프론트가 분기하는 안정 식별자다 - 한 번 공개된 번호는
 * 기능이 빠져도 다른 의미로 재사용하지 않는다(결번 유지).
 */
@Getter
@AllArgsConstructor
public enum BusinessRegistrationErrorCode implements BaseErrorCode {

    /**
     * 서명 위변조·만료·업로더 불일치·잘못된 prefix를 <b>구분하지 않고</b> 하나로 묶는다.
     * 어느 검사에서 걸렸는지 알려주면 공격자가 토큰을 조금씩 바꿔가며 어느 필드가 문제인지
     * 알아내는 판별기(validity oracle)가 된다. 사유는 서버 로그에만 남긴다.
     */
    INVALID_UPLOAD_TOKEN(HttpStatus.BAD_REQUEST, "BUSINESS_400_001",
            "사업자등록증 파일 정보가 유효하지 않습니다. 파일을 다시 업로드해 주세요."),

    /**
     * 서버 설정 누락. 시크릿이 없으면 업로드 토큰을 아무나 위조할 수 있으므로,
     * 서명 없이 통과시키지 않고 기능 자체를 막는다.
     */
    UPLOAD_TOKEN_SECRET_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_500_002",
            "사업자등록증 업로드를 사용할 수 없습니다. 관리자에게 문의해 주세요."),

    /** 서버 설정 누락(버킷 미지정) 또는 S3 업로드 자체가 실패했다. */
    DOCUMENT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_500_003",
            "사업자등록증 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
