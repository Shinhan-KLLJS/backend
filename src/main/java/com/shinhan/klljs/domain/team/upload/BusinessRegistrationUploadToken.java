package com.shinhan.klljs.domain.team.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 업로드 토큰의 payload (docs/team-creation-api-spec.md 5절 "documentStorageKey 검증").
 *
 * 업로드 API가 이 값을 HMAC-SHA256으로 서명해 프론트에 내려주고, 팀 생성/재제출 시 서버가
 * 서명을 확인한 뒤 꺼내 쓴다. 프론트는 내용을 해석할 필요 없이 그대로 되돌려 보내기만 한다.
 *
 * <h3>왜 업태·종목이 여기 들어있나</h3>
 * 광고업 분류의 유일한 입력인데 국세청이 확인해주지 않는다. 폼으로 받으면 <b>실제로는 음식점업인
 * 사람이 업태를 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다</b>
 * (server-verification-spec.md §7). 서명 대상에 넣어두면 값을 바꾸는 순간 서명이 깨진다 -
 * 이것이 그 우회를 막는 유일한 장치다.
 *
 * @param purpose               항상 {@link BusinessRegistrationUploadTokenSigner#PURPOSE}.
 *                              다른 용도로 발급된 토큰을 여기에 들고 오는 것을 막는다
 * @param s3Key                 서버가 정한 실제 S3 키. DB에는 이 값만 저장한다
 * @param uploaderId            업로드한 사용자. 요청자와 다르면 거부한다 (남의 사업자등록증으로 팀 생성 차단)
 * @param expiresAtEpochSecond  만료 시각 (UTC epoch seconds)
 * @param businessType          OCR이 읽은 업태. 인식 실패 시 null
 * @param businessItem          OCR이 읽은 종목. 인식 실패 시 null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessRegistrationUploadToken(
        String purpose,
        String s3Key,
        Long uploaderId,
        long expiresAtEpochSecond,
        String businessType,
        String businessItem
) {
}
