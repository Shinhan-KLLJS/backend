package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

/**
 * 사업자등록증 원본을 private S3 버킷에 저장한다.
 *
 * 대표자 성명·사업장 주소·사업자등록번호가 함께 찍힌 민감 문서라 <b>공개 URL을 만들지 않는다</b>
 * (mvp-database-erd.md 5절). 이 플로우에서는 프론트가 이미지 자체에 접근할 필요가 없어
 * presigned URL조차 발급하지 않는다 - OCR 결과 텍스트만 주고받는다.
 */
@Slf4j
@Component
public class BusinessRegistrationDocumentStorage {

    private final S3Client s3Client;
    private final BusinessRegistrationUploadProperties properties;

    public BusinessRegistrationDocumentStorage(
            @Qualifier("businessRegistrationS3Client") S3Client s3Client,
            BusinessRegistrationUploadProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 저장하고 <b>서버가 정한</b> S3 키를 돌려준다.
     *
     * 키는 추측할 수 없는 UUID로 만든다. 사용자가 보낸 파일명은 쓰지 않는다 -
     * 경로 조작(`../`)과 다른 사용자의 문서를 덮어쓰는 것을 원천 차단하기 위해서다.
     * 확장자도 파일명이 아니라 매직바이트로 판별한 실제 형식에서 가져온다.
     *
     * @throws GeneralException 버킷 미설정 또는 업로드 실패 시 BUSINESS_500_003
     */
    public String store(byte[] content, DocumentType type) {
        if (!properties.hasBucket()) {
            log.error("사업자등록증 저장 버킷이 설정되지 않았습니다. "
                    + "app.upload.business-registration.bucket(BUSINESS_REGISTRATION_BUCKET)을 확인하세요.");
            throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_STORAGE_FAILED);
        }

        String key = properties.keyPrefix() + UUID.randomUUID() + "." + type.extension();

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentTypeOf(type))
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException e) {
            // 예외 메시지에 버킷/키가 실려 나갈 수 있어 사용자에게는 일반 문구만 내보낸다.
            log.error("사업자등록증 S3 업로드 실패: key={}", key, e);
            throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_STORAGE_FAILED);
        }

        return key;
    }

    /** 저장한 형식을 그대로 적어둔다. 브라우저가 이 값을 보고 렌더링하지는 않지만(비공개 버킷) 메타데이터로 남긴다. */
    private static String contentTypeOf(DocumentType type) {
        return switch (type) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case PDF -> "application/pdf";
        };
    }
}
