package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.upload.OcrResult;

/**
 * 사업자등록증 업로드 응답 (docs/team-creation-api-spec.md 4절).
 *
 * @param documentStorageKey 업로드된 파일을 가리키는 <b>서명 토큰</b>. 프론트는 내용을 해석할 필요
 *                           없이 그대로 받아서 팀 생성 요청에 실어 보낸다. 원본 S3 키가 아니다
 * @param ocrResult          사용자가 화면에서 확인할 추정값. 인식 실패한 필드는 null
 */
public record BusinessRegistrationUploadResponse(
        String documentStorageKey,
        OcrFields ocrResult
) {

    /**
     * OCR이 읽은 값을 <b>그대로</b> 프론트에 보여준다. 사용자가 화면에서 확인하고 그대로 팀을
     * 만드는 흐름이다 - 자동 검증·판정은 MVP에서 하지 않는다.
     *
     * 업태·종목은 응답과 별개로 documentStorageKey 토큰 안에도 서명돼 있다. 팀 생성 시 DB에
     * 저장되는 값은 토큰 쪽을 쓴다 - 전송 구간에서 변조된 값이 아니라 OCR 원본임을 보증한다.
     */
    public record OcrFields(
            String companyName,
            String representativeName,
            String businessNumber,
            String businessOpeningDate,
            String businessType,
            String businessItem
    ) {
        static OcrFields from(OcrResult ocr) {
            return new OcrFields(
                    ocr.companyName(),
                    ocr.representativeName(),
                    ocr.businessNumber(),
                    ocr.businessOpeningDate(),
                    ocr.businessType(),
                    ocr.businessItem());
        }
    }

    public static BusinessRegistrationUploadResponse of(String documentStorageKey, OcrResult ocr) {
        return new BusinessRegistrationUploadResponse(documentStorageKey, OcrFields.from(ocr));
    }
}
