package com.shinhan.klljs.domain.team.upload;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파일 형식 판별.
 *
 * <b>확장자와 Content-Type은 클라이언트가 마음대로 붙여 보내는 값이라 믿지 않는다.</b>
 * 실행 파일의 확장자만 {@code .png}로 바꿔도 통과하기 때문이다. 파일 앞부분의 매직바이트만 본다.
 */
class DocumentTypeTest {

    @Test
    void detect_recognizesAllowedFormatsByTheirSignature() {
        assertThat(DocumentType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .isEqualTo(DocumentType.JPEG);
        assertThat(DocumentType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .isEqualTo(DocumentType.PNG);
        assertThat(DocumentType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(DocumentType.PDF);
    }

    /**
     * 확장자만 바꾼 실행 파일. 이게 통과하면 우리 버킷이 악성코드 배포처가 된다.
     * ("MZ"는 Windows PE 실행 파일의 시그니처다.)
     */
    @Test
    void detect_rejectsAnExecutableRenamedToLookLikeAnImage() {
        byte[] windowsExecutable = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThat(DocumentType.detect(windowsExecutable)).isNull();
    }

    @Test
    void detect_rejectsFormatsWeDoNotAccept() {
        // GIF - OCR이 다루지 못하고 사업자등록증으로 올릴 일도 없다.
        assertThat(DocumentType.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isNull();
        // 텍스트 파일
        assertThat(DocumentType.detect("hello".getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    void detect_rejectsEmptyOrTruncatedInput() {
        assertThat(DocumentType.detect(null)).isNull();
        assertThat(DocumentType.detect(new byte[0])).isNull();
        // PNG 시그니처가 중간에 잘렸다.
        assertThat(DocumentType.detect(new byte[] {(byte) 0x89, 'P'})).isNull();
    }

    /** S3 키의 확장자는 사용자가 보낸 파일명이 아니라 실제로 판별한 형식에서 온다. */
    @Test
    void extension_comesFromTheDetectedFormat() {
        assertThat(DocumentType.JPEG.extension()).isEqualTo("jpg");
        assertThat(DocumentType.PNG.extension()).isEqualTo("png");
        assertThat(DocumentType.PDF.extension()).isEqualTo("pdf");
    }
}
