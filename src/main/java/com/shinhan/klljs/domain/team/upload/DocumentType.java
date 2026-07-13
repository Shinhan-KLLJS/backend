package com.shinhan.klljs.domain.team.upload;

import java.util.Arrays;

/**
 * 사업자등록증으로 받아주는 파일 형식.
 *
 * <b>확장자나 Content-Type을 믿지 않는다.</b> 둘 다 클라이언트가 마음대로 붙여 보내는 값이라,
 * 실행 파일의 확장자만 {@code .png}로 바꿔도 통과한다. 파일 앞부분의 매직바이트(파일 시그니처)로
 * 실제 내용을 확인한다.
 *
 * OCR Lambda가 처리할 수 있는 형식(jpg/png/pdf/tiff) 중에서, 사용자가 실제로 올릴 법한
 * 세 가지만 허용한다. TIFF는 브라우저가 미리보기도 못 하고 사용자가 만들 일도 없어 뺐다.
 */
public enum DocumentType {

    JPEG("jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),

    PNG("png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),

    /** {@code %PDF-} */
    PDF("pdf", new byte[] {'%', 'P', 'D', 'F', '-'});

    /** 어떤 시그니처든 이만큼만 읽으면 판별할 수 있다. */
    static final int MAGIC_BYTES_TO_READ = 8;

    private final String extension;
    private final byte[] magicBytes;

    DocumentType(String extension, byte[] magicBytes) {
        this.extension = extension;
        this.magicBytes = magicBytes;
    }

    /** S3 키에 붙일 확장자. 사용자가 보낸 파일명이 아니라 실제 내용에서 정한 값이다. */
    public String extension() {
        return extension;
    }

    /**
     * 파일 앞부분으로 형식을 판별한다. 아는 형식이 아니면 null이다.
     * (호출부가 에러 코드를 붙여야 해서 Optional 대신 null을 쓴다 - 도메인 예외를 여기서 던지지 않는다.)
     */
    public static DocumentType detect(byte[] header) {
        if (header == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.matches(header))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(byte[] header) {
        if (header.length < magicBytes.length) {
            return false;
        }
        return Arrays.equals(header, 0, magicBytes.length, magicBytes, 0, magicBytes.length);
    }
}
