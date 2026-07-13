package com.shinhan.klljs.domain.team.verification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 국세청 진위확인·상태조회를 합친 결과 (server-verification-spec.md §2).
 *
 * <b>진위 확인 통과는 "영업 중"이라는 뜻이 아니다.</b> 실제 사업자등록증으로 확인한 결과,
 * 진위 확인을 통과하면서 {@code b_stt}가 폐업자인 경우가 있다. 사업자등록증 자체는 진짜지만
 * 이미 폐업한 사업자다. 그래서 진위({@link #certificateValidity})와 영업 상태({@link #active})를
 * 별도 필드로 분리해 두고, 최종 판정에서도 각각 다른 규칙으로 검사한다.
 *
 * @param certificateValidity 진위 확인 결과. {@code /validate} 응답의 {@code valid}
 * @param registered          국세청 등록 여부. {@code b_stt_cd}가 비어 있지 않음
 * @param active              계속사업자 여부. {@code b_stt_cd == "01"} (휴업자·폐업자는 false)
 * @param businessStatus      사업자 상태 문구. {@code b_stt} (예: "계속사업자", "폐업자")
 * @param taxType             과세 유형. {@code tax_type}
 * @param closingDate         폐업일 {@code YYYYMMDD}. {@code end_dt}. 폐업자가 아니면 null
 * @param invalidMessage      진위확인 실패 사유. {@code valid_msg}
 */
public record BusinessCheckResult(
        CertificateValidity certificateValidity,
        boolean registered,
        boolean active,
        String businessStatus,
        String taxType,
        String closingDate,
        String invalidMessage
) {

    /** 계속사업자를 뜻하는 b_stt_cd 값. 휴업자(02)·폐업자(03)는 여기에 해당하지 않는다. */
    private static final String ACTIVE_STATUS_CODE = "01";

    private static final DateTimeFormatter CLOSING_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 국세청 응답 필드에서 판정에 필요한 값을 뽑아낸다.
     *
     * <b>미등록 사업자는 반드시 {@code b_stt_cd}로 판단한다.</b> 등록되지 않은 번호는 {@code b_stt_cd}가
     * 빈 문자열로 오고 {@code b_stt}는 아예 없다. {@code tax_type} 문구("국세청에 등록되지 않은
     * 사업자등록번호입니다.")로 판단하면 국세청이 문구를 바꿀 때 조용히 깨진다.
     *
     * @param statusCode {@code b_stt_cd}. 상태 정보를 받지 못했으면 null
     */
    public static BusinessCheckResult of(CertificateValidity certificateValidity, String invalidMessage,
                                          String statusCode, String businessStatus,
                                          String taxType, String closingDate) {
        String code = (statusCode == null) ? "" : statusCode.trim();
        return new BusinessCheckResult(
                certificateValidity,
                !code.isEmpty(),
                ACTIVE_STATUS_CODE.equals(code),
                businessStatus,
                taxType,
                closingDate,
                invalidMessage);
    }

    /**
     * 영업 중이 아닐 때 반려 메시지에 덧붙일 부가 설명.
     * 폐업일을 함께 알려주면 사용자가 상황을 이해하기 쉽다 (§2.2).
     * 예) {@code "상태: 폐업자, 폐업일: 2024-06-24"}
     */
    public String inactiveDetail() {
        StringBuilder detail = new StringBuilder();
        if (businessStatus != null && !businessStatus.isBlank()) {
            detail.append("상태: ").append(businessStatus);
        }

        String formattedClosingDate = formatClosingDate();
        if (formattedClosingDate != null) {
            if (!detail.isEmpty()) {
                detail.append(", ");
            }
            detail.append("폐업일: ").append(formattedClosingDate);
        }
        return detail.toString();
    }

    /** {@code YYYYMMDD}를 사람이 읽는 형식으로. 값이 없거나 형식이 어긋나면 null이라 메시지에서 생략된다. */
    private String formatClosingDate() {
        if (closingDate == null || closingDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(closingDate.trim(), DateTimeFormatter.BASIC_ISO_DATE).format(CLOSING_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
