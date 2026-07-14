package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationUploadResponse;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * 사업자등록증 파일을 받아 S3에 저장하고 OCR 결과를 즉시 돌려준다
 * (docs/team-creation-api-spec.md 4절).
 *
 * <b>DB에는 아무것도 쓰지 않는다.</b> 이 시점엔 아직 팀이 없고, 사용자가 값을 확인하기도 전이다.
 * OCR 값으로 기존 APPROVED 행을 덮어쓰는 사고를 막기 위해서이기도 하다
 * (server-verification-spec.md §6). 그래서 @Transactional도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessRegistrationUploadService {

    private final BusinessRegistrationDocumentStorage documentStorage;
    private final BusinessRegistrationOcrClient ocrClient;
    private final BusinessRegistrationUploadTokenSigner tokenSigner;
    private final BusinessRegistrationUploadRateLimiter rateLimiter;
    private final BusinessRegistrationUploadProperties properties;
    private final DocumentContentValidator contentValidator;

    public BusinessRegistrationUploadResponse upload(Long userId, MultipartFile file) {
        // 파일 검증보다 먼저 센다. 잘못된 파일을 반복해서 던지는 것으로 한도를 우회할 수 없게 한다.
        rateLimiter.checkAndRecord(userId);

        byte[] content = readAndValidate(file);
        DocumentType type = detectType(content);

        // 매직바이트는 앞 몇 바이트일 뿐이라 헤더만 흉내 낸 파일도 통과한다. 실제로 열어봐서
        // 내용이 온전한지까지 확인한 뒤에야 S3와 OCR(호출당 과금)로 넘긴다.
        contentValidator.validate(content, type);

        String s3Key = documentStorage.store(content, type);
        OcrResult ocr = ocrClient.extract(s3Key);

        // 토큰은 업로드가 성공한 뒤에만 발급한다. 그래야 "유효한 토큰 = 그 객체가 존재"가 성립해서
        // 팀 생성 시점에 S3를 다시 조회하지 않아도 된다 (문서 5절).
        //
        // 업태·종목은 응답(화면 표시용)과 별개로 이 토큰 안에도 서명해 담는다. 팀 생성 시 DB에
        // 저장되는 값은 토큰 쪽을 쓴다 - 전송 구간에서 변조된 값이 OCR 원본 행세를 할 수 없다.
        String documentStorageKey = tokenSigner.sign(s3Key, userId, ocr.businessType(), ocr.businessItem());

        return BusinessRegistrationUploadResponse.of(documentStorageKey, ocr);
    }

    /**
     * 확장자와 Content-Type은 클라이언트가 마음대로 붙여 보내는 값이라 믿지 않는다.
     * 여기서는 크기만 보고, 실제 형식은 매직바이트로 판별한다.
     */
    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_MISSING);
        }
        // multipart 설정(max-file-size)이 먼저 막아주지만, 그 값이 바뀌어도 서비스 계약은 유지되도록
        // 여기서 한 번 더 확인한다.
        if (file.getSize() > properties.maxFileBytes()) {
            throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_TOO_LARGE);
        }

        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("업로드 파일을 읽지 못했습니다.", e);
            throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_STORAGE_FAILED);
        }
    }

    /** 파일 앞부분의 시그니처로 실제 형식을 정한다. 확장자만 바꾼 파일은 여기서 걸린다. */
    private static DocumentType detectType(byte[] content) {
        byte[] header = Arrays.copyOf(content, Math.min(content.length, DocumentType.MAGIC_BYTES_TO_READ));

        DocumentType type = DocumentType.detect(header);
        if (type == null) {
            throw new GeneralException(BusinessRegistrationErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
        }
        return type;
    }
}
