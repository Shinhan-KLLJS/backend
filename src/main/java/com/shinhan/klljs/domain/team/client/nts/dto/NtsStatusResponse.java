package com.shinhan.klljs.domain.team.client.nts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 상태조회 응답 {@code POST /status} (server-verification-spec.md §2.2).
 * {@code status_code}가 "OK"가 아니면 실패로 처리하고, 결과는 {@code data} 배열의 첫 항목만 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NtsStatusResponse(
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("data") List<NtsBusinessStatus> data
) {
}
