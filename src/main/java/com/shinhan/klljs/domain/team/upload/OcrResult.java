package com.shinhan.klljs.domain.team.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OCR Lambda가 읽어낸 사업자등록증 필드 (docs/team-creation-api-spec.md 8절 "OCR Lambda 응답 계약").
 *
 * <b>모두 추정값이다.</b> 프론트에 그대로 보여주고 사용자가 확인한 뒤 팀 생성에 쓴다.
 * 업태·종목은 응답에 보여주는 것과 별개로 서명 토큰에도 실어 나른다 - 저장되는 값이
 * OCR 원본임을 보증하기 위해서다 ({@link BusinessRegistrationUploadToken} 참고).
 *
 * 인식에 실패한 필드는 null이다. <b>OCR이 아예 실패해도 업로드는 성공</b>이고, 이 경우
 * {@link #empty()}로 모든 필드가 null인 결과를 만든다 - 사용자가 직접 입력하면 되기 때문이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrResult(
        String companyName,
        String representativeName,
        String businessNumber,
        String businessOpeningDate,
        String businessType,
        String businessItem
) {

    /** OCR을 못 했거나 실패했을 때. 업로드 자체는 성공으로 처리한다. */
    public static OcrResult empty() {
        return new OcrResult(null, null, null, null, null, null);
    }
}
