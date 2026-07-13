package com.shinhan.klljs.domain.team.client.nts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 사업자 상태 객체 (server-verification-spec.md §2.2).
 * {@code /status} 응답의 data 원소이자, {@code /validate} 응답에 함께 실려 오는 status 객체이기도 하다.
 *
 * <b>등록되지 않은 번호는 {@code b_stt_cd}가 빈 문자열이고 {@code b_stt}는 아예 없다.</b>
 * 미등록 판단은 {@code tax_type} 문구가 아니라 {@code b_stt_cd}가 비었는지로만 한다.
 *
 * @param businessStatusCode {@code b_stt_cd}: 01=계속사업자, 02=휴업자, 03=폐업자, 빈 값=미등록
 * @param closingDate        {@code end_dt}: 폐업일 YYYYMMDD. 폐업자일 때만 들어온다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NtsBusinessStatus(
        @JsonProperty("b_no") String businessNumber,
        @JsonProperty("b_stt") String businessStatus,
        @JsonProperty("b_stt_cd") String businessStatusCode,
        @JsonProperty("tax_type") String taxType,
        @JsonProperty("end_dt") String closingDate
) {
}
