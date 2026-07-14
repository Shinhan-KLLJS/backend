package com.shinhan.klljs.domain.team.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * application.yml의 app.upload.business-registration.* 값을 주입받는다
 * (VisionSqsProperties와 같은 패턴).
 *
 * 로컬/테스트에서는 bucket과 ocrFunctionName이 비어 있어도 컨텍스트가 떠야 한다 - 실제 AWS에
 * 붙지 않고도 테스트가 돌아야 하기 때문이다. 대신 값이 비면 해당 기능을 호출하는 시점에 막는다.
 */
@Component
public class BusinessRegistrationUploadProperties {

    private final String tokenSecret;
    private final long tokenTtlSeconds;
    private final String bucket;
    private final String region;
    private final String keyPrefix;
    private final long maxFileBytes;
    private final long maxImagePixels;
    private final int maxPdfPages;
    private final String ocrFunctionName;
    private final int ocrTimeoutMs;

    public BusinessRegistrationUploadProperties(
            @Value("${app.upload.business-registration.token-secret}") String tokenSecret,
            @Value("${app.upload.business-registration.token-ttl-seconds}") long tokenTtlSeconds,
            @Value("${app.upload.business-registration.bucket}") String bucket,
            @Value("${app.upload.business-registration.region}") String region,
            @Value("${app.upload.business-registration.key-prefix}") String keyPrefix,
            @Value("${app.upload.business-registration.max-file-bytes}") long maxFileBytes,
            @Value("${app.upload.business-registration.max-image-pixels}") long maxImagePixels,
            @Value("${app.upload.business-registration.max-pdf-pages}") int maxPdfPages,
            @Value("${app.upload.business-registration.ocr-function-name}") String ocrFunctionName,
            @Value("${app.upload.business-registration.ocr-timeout-ms}") int ocrTimeoutMs
    ) {
        this.tokenSecret = tokenSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.bucket = bucket;
        this.region = region;
        this.keyPrefix = keyPrefix;
        this.maxFileBytes = maxFileBytes;
        this.maxImagePixels = maxImagePixels;
        this.maxPdfPages = maxPdfPages;
        this.ocrFunctionName = ocrFunctionName;
        this.ocrTimeoutMs = ocrTimeoutMs;
    }

    /** 시크릿이 없으면 토큰을 아무나 위조할 수 있으므로 서명/검증 자체를 거부한다. */
    public boolean hasTokenSecret() {
        return tokenSecret != null && !tokenSecret.isBlank();
    }

    public boolean hasBucket() {
        return bucket != null && !bucket.isBlank();
    }

    /** OCR은 없어도 업로드가 성립한다 (모든 필드를 null로 내려주고 사용자가 직접 입력). */
    public boolean hasOcrFunction() {
        return ocrFunctionName != null && !ocrFunctionName.isBlank();
    }

    public String tokenSecret() {
        return tokenSecret;
    }

    public long tokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public String bucket() {
        return bucket;
    }

    public String region() {
        return region;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    /**
     * 디코딩 허용 픽셀 수 상한 (가로×세로). 파일 크기(maxFileBytes)와 별개다 - 고압축 PNG는
     * 몇 MB로도 수억 픽셀을 선언할 수 있어, 이 상한 없이는 decompression bomb 한 장에 OOM이 난다.
     */
    public long maxImagePixels() {
        return maxImagePixels;
    }

    /** 사업자등록증은 한 장짜리 문서다. 상한을 넘는 PDF는 OCR 비용을 태우려는 시도로 본다. */
    public int maxPdfPages() {
        return maxPdfPages;
    }

    public String ocrFunctionName() {
        return ocrFunctionName;
    }

    public int ocrTimeoutMs() {
        return ocrTimeoutMs;
    }
}
