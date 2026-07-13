package com.shinhan.klljs.domain.team.client.nts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;

import java.util.List;

/**
 * 진위확인 요청 본문 {@code POST /validate} (server-verification-spec.md §2.1).
 *
 * 사업자명(b_nm), 업태, 종목, 주소는 <b>보내지 않는다.</b> OCR 값의 흔들림이 커서
 * 일치 검증에 쓰면 정상 사업자가 반려된다. 국세청이 요구하는 세 필드만 보낸다.
 */
public record NtsValidateRequest(List<Business> businesses) {

    public static NtsValidateRequest of(NormalizedBusinessInput input) {
        return new NtsValidateRequest(List.of(new Business(
                input.businessNumber(), input.openingDateText(), input.representativeName())));
    }

    /**
     * @param businessNumber     {@code b_no} 숫자 10자리
     * @param openingDate        {@code start_dt} YYYYMMDD
     * @param representativeName {@code p_nm} 공백·"외N명"을 제거한 대표자명
     */
    public record Business(
            @JsonProperty("b_no") String businessNumber,
            @JsonProperty("start_dt") String openingDate,
            @JsonProperty("p_nm") String representativeName
    ) {
    }
}
