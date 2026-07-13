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
