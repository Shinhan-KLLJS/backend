package com.shinhan.klljs.domain.team.client.nts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 상태조회 요청 본문 {@code POST /status} (server-verification-spec.md §2.2).
 *
 * 사업자등록번호만으로 등록 여부와 영업 상태를 조회한다. 진위 확인이 아니므로
 * <b>이것만으로 승인하면 안 된다.</b> 남의 실존 사업자번호를 넣어도 통과한다.
 */
public record NtsStatusRequest(@JsonProperty("b_no") List<String> businessNumbers) {

    public static NtsStatusRequest of(String businessNumber) {
        return new NtsStatusRequest(List.of(businessNumber));
    }
}
