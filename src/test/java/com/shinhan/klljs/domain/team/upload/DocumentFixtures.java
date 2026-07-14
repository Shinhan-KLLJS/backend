package com.shinhan.klljs.domain.team.upload;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 업로드 테스트가 쓸 <b>실제로 열리는</b> 파일들.
 *
 * 전에는 매직바이트 몇 개만 흉내 낸 배열을 썼는데, 이제 서버가 이미지를 디코딩하고 PDF를 파싱하므로
 * (DocumentContentValidator) 그런 가짜 파일은 전부 거부된다. 테스트도 진짜 파일을 써야 한다 -
 * 그래야 "정상 파일은 통과한다"를 실제로 검증하는 셈이 된다.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {
    }

    /** 디코딩되는 진짜 PNG. */
    public static byte[] png() {
        return image("png");
    }

    /** 디코딩되는 진짜 JPEG. */
    public static byte[] jpeg() {
        return image("jpg");
    }

    private static byte[] image(String format) {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 시그니처는 진짜인데 내용이 깨진 PNG.
     * 매직바이트 검사는 통과하고 디코딩에서 걸려야 한다 - 이 둘을 가르는 것이 핵심이다.
     */
    public static byte[] corruptedPng() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] corrupted = new byte[64];
        System.arraycopy(header, 0, corrupted, 0, header.length);
        return corrupted;
    }

    /**
     * 헤더가 50000×50000(25억 픽셀)을 선언하는 PNG - decompression bomb의 최소 재현.
     *
     * 파일은 수십 바이트지만 그대로 디코딩하면 픽셀당 4바이트, 약 10GB 힙이 필요해 OOM이 난다.
     * 진짜 픽셀 데이터를 만들면 테스트 JVM부터 죽으므로, 시그니처와 IHDR 청크(CRC 포함)만
     * 손으로 만들어 "헤더는 읽히지만 디코딩하면 안 되는" 파일을 흉내 낸다.
     */
    public static byte[] hugeDimensionPng() {
        return pngDeclaring(50_000, 50_000, 8);
    }

    /**
     * 픽셀 수(25MP)는 상한(30MP) 안이지만 <b>16비트 RGBA(픽셀당 8바이트)</b>라 디코딩하면
     * 약 200MB가 되는 PNG. 픽셀 수만 보면 통과하고 바이트 예산(120MB)으로는 걸려야 한다.
     */
    public static byte[] sixteenBitPngWithinPixelCap() {
        return pngDeclaring(5_000, 5_000, 16);
    }

    /** 시그니처와 IHDR 청크(CRC 포함)만 손으로 만든 PNG. 헤더는 읽히지만 픽셀 데이터는 없다. */
    private static byte[] pngDeclaring(int width, int height, int bitDepth) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        // IHDR data(13바이트): width, height, bit depth, color type 6(RGBA), 압축/필터/인터레이스 0
        java.nio.ByteBuffer ihdrData = java.nio.ByteBuffer.allocate(13);
        ihdrData.putInt(width).putInt(height)
                .put((byte) bitDepth).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);

        // CRC는 청크 타입("IHDR")과 data에 대해 계산한다 - CRC가 틀리면 리더가 헤더조차 안 읽는다.
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("IHDR".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        crc.update(ihdrData.array());

        java.nio.ByteBuffer png = java.nio.ByteBuffer.allocate(signature.length + 4 + 4 + 13 + 4);
        png.put(signature)
                .putInt(13)
                .put("IHDR".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .put(ihdrData.array())
                .putInt((int) crc.getValue());
        return png.array();
    }

    /** 헤더만 흉내 낸 가짜 PDF. 매직바이트("%PDF-")는 맞지만 파싱되지 않는다. */
    public static byte[] fakePdf() {
        return "%PDF-1.7\n이건 PDF가 아니다".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 열리는 진짜 PDF. */
    public static byte[] pdf(int pages) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 사용자 암호가 걸린 PDF. 암호 없이는 열리지 않는다. */
    public static byte[] passwordProtectedPdf() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission()));
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
