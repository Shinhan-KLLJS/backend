package com.shinhan.klljs.domain.team.client.nts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 진위확인 응답 {@code POST /validate} (server-verification-spec.md §2.1).
 * {@code status_code}가 "OK"가 아니면 실패로 처리하고, 결과는 {@code data} 배열의 첫 항목만 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NtsValidateResponse(
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("data") List<Data> data
) {

    /**
     * @param valid    "01"이면 진위 확인 통과. <b>영업 중이라는 뜻은 아니다.</b>
     * @param validMsg 진위확인 실패 사유
     * @param status   사업자 상태 객체. 응답에 없으면 {@code /status}를 한 번 더 호출해 채운다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("valid") String valid,
            @JsonProperty("valid_msg") String validMsg,
            @JsonProperty("status") NtsBusinessStatus status
    ) {
    }
}
