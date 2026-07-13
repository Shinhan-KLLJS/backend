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
            "사업자등록증 저장에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    /** 확장자만 바꾼 파일로 우회할 수 없도록 매직바이트까지 확인한다. */
    UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.BAD_REQUEST, "BUSINESS_400_002",
            "JPG, PNG, PDF 형식의 사업자등록증만 업로드할 수 있습니다."),

    DOCUMENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "BUSINESS_400_003",
            "사업자등록증 파일이 너무 큽니다."),

    DOCUMENT_MISSING(HttpStatus.BAD_REQUEST, "BUSINESS_400_004",
            "사업자등록증 파일을 첨부해 주세요."),

    /**
     * 매직바이트는 맞지만 실제로는 열리지 않는 파일 (team-creation-api-spec.md 4절).
     *
     * 헤더 몇 바이트만 흉내 낸 파일은 시그니처 검사를 통과한다. 그대로 두면 S3에 저장되고
     * OCR Lambda까지 호출된 뒤에야(= 비용) 아무것도 못 읽었다는 결과가 돌아온다.
     * 이미지는 디코딩을, PDF는 파싱을 실제로 해봐서 업로드 시점에 걸러낸다.
     */
    DOCUMENT_UNREADABLE(HttpStatus.BAD_REQUEST, "BUSINESS_400_005",
            "파일이 손상되어 열 수 없습니다. 다시 촬영하거나 스캔해서 올려 주세요."),

    /** 암호가 걸린 PDF는 OCR이 내용을 읽을 수 없다. 저장하기 전에 막고 사용자에게 알린다. */
    DOCUMENT_PASSWORD_PROTECTED(HttpStatus.BAD_REQUEST, "BUSINESS_400_006",
            "암호가 걸린 PDF는 올릴 수 없습니다. 암호를 해제한 뒤 다시 시도해 주세요."),

    /**
     * 사업자등록증은 한 장짜리 문서다. 수십~수백 페이지짜리 PDF는 사업자등록증이 아니거나,
     * OCR 비용을 태우려는 시도다. OCR은 페이지 수에 비례해 과금된다.
     */
    DOCUMENT_TOO_MANY_PAGES(HttpStatus.BAD_REQUEST, "BUSINESS_400_007",
            "사업자등록증 PDF의 페이지가 너무 많습니다. 사업자등록증 페이지만 올려 주세요."),

    /**
     * 인코딩된 파일 크기(BUSINESS_400_003)와 별개로 <b>디코딩된 픽셀 수</b>가 상한을 넘는 이미지.
     *
     * 고압축 PNG는 몇 MB로도 수억 픽셀을 선언할 수 있고(decompression bomb), 그대로 디코딩하면
     * 픽셀당 4바이트짜리 래스터가 힙에 잡혀 단일 EC2 인스턴스가 OOM으로 내려간다.
     * 헤더의 가로×세로만 읽어 디코딩 전에 거른다.
     */
    DOCUMENT_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "BUSINESS_400_008",
            "이미지 해상도가 너무 높습니다. 해상도를 낮춰 다시 올려 주세요."),

    /**
     * 업로드 한 번마다 OCR Lambda가 호출되고 OCR은 호출당 과금이다.
     * 인증만으로 무제한 호출이 가능하면 로그인한 사용자 하나가 비용을 계속 태울 수 있다.
     */
    UPLOAD_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "BUSINESS_429_001",
            "사업자등록증 업로드 횟수를 초과했습니다. 24시간 후에 다시 시도해 주세요."),

    /**
     * 인증된 사용자의 users 행이 없다 - 정상 경로에서는 나올 수 없고, 탈퇴 직후의 잔존 토큰
     * 같은 경계 상황에서만 나온다. "팀 없음"(404)과 구분해 주지 않는 이유는 팀의 존재 여부가
     * 외부에 새어 나가지 않게 하기 위해서다.
     */
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "BUSINESS_403_001", "해당 팀의 활성 멤버가 아닙니다."),

    /**
     * 사용자는 팀 하나에만 속한다 (UserMeResponse가 teamId를 하나만 반환하는 것도 이 가정 위에 있다).
     * 막지 않으면 두 번째 팀은 만들어지되 사용자가 접근할 수 없는 유령 팀이 된다.
     */
    ALREADY_HAS_TEAM(HttpStatus.CONFLICT, "BUSINESS_409_002",
            "이미 소속된 팀이 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
