package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.upload.OcrResult;

/**
 * 사업자등록증 업로드 응답 (docs/team-creation-api-spec.md 4절).
 *
 * @param documentStorageKey 업로드된 파일을 가리키는 <b>서명 토큰</b>. 프론트는 내용을 해석할 필요
 *                           없이 그대로 받아서 팀 생성/재제출 요청에 실어 보낸다. 원본 S3 키가 아니다
 * @param ocrResult          사용자가 화면에서 확인·수정할 추정값. 인식 실패한 필드는 null
 */
public record BusinessRegistrationUploadResponse(
        String documentStorageKey,
        OcrFields ocrResult
) {

    /**
     * <b>업태·종목은 여기 없다.</b> 광고업 분류의 유일한 입력인데 국세청이 확인해주지 않아서,
     * 사용자에게 보여주면 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다
     * (server-verification-spec.md §7). 그래서 화면에 노출하지 않고 documentStorageKey 토큰
     * 안에 서명해 넣어 나른다.
     */
    public record OcrFields(
            String companyName,
            String representativeName,
            String businessNumber,
            String businessOpeningDate
    ) {
        static OcrFields from(OcrResult ocr) {
            return new OcrFields(
                    ocr.companyName(),
                    ocr.representativeName(),
                    ocr.businessNumber(),
                    ocr.businessOpeningDate());
        }
    }

    public static BusinessRegistrationUploadResponse of(String documentStorageKey, OcrResult ocr) {
        return new BusinessRegistrationUploadResponse(documentStorageKey, OcrFields.from(ocr));
    }
}
