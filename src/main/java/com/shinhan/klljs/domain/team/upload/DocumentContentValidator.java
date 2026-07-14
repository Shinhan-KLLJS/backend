package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 파일을 실제로 열어봐서 내용이 온전한지 확인한다 (team-creation-api-spec.md 4절).
 *
 * <b>매직바이트 검사({@link DocumentType})만으로는 부족하다.</b> 시그니처는 파일 앞 몇 바이트일 뿐이라,
 * {@code %PDF-}나 PNG 헤더만 붙인 쓰레기 파일도 그대로 통과한다. 그러면 S3에 저장되고 OCR Lambda까지
 * 호출된 뒤에야(= 호출당 과금) "아무것도 못 읽었다"는 결과가 돌아온다. 사용자에게도 "다시 올려라"라는
 * 안내가 한 박자 늦게 나간다.
 *
 * 그래서 저장 전에 이미지는 디코딩을, PDF는 파싱을 실제로 수행한다. 여기서 걸러내면 S3도 OCR도 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentContentValidator {

    private final BusinessRegistrationUploadProperties properties;

    /**
     * 형식별로 내용을 검증한다. 문제가 있으면 {@link GeneralException}을 던진다.
     *
     * @param content 업로드된 파일 전체
     * @param type    매직바이트로 판별된 형식
     */
    public void validate(byte[] content, DocumentType type) {
        if (type == DocumentType.PDF) {
            validatePdf(content);
        } else {
            validateImage(content, type);
        }
    }

    /**
     * <b>헤더의 가로×세로를 먼저 확인한 뒤</b> 실제 디코딩을 시도한다. 픽셀까지 만들어져야 통과다.
     *
     * 순서가 중요하다: 파일 크기 제한(10MB)은 인코딩된 바이트만 보므로, 고압축 PNG는 몇 MB로도
     * 수억 픽셀을 선언할 수 있다(decompression bomb). 그대로 디코딩하면 픽셀당 4바이트짜리
     * 래스터가 힙에 잡혀 인스턴스가 OOM으로 내려간다. {@link ImageReader#getWidth}는 헤더만
     * 읽고 픽셀을 만들지 않으므로, 여기서 상한을 넘는 이미지를 메모리 할당 없이 거른다.
     *
     * ImageIO는 읽을 수 없으면 예외를 던지기도 하고 조용히 null을 돌려주기도 한다 - 둘 다 실패로 본다.
     */
    private void validateImage(byte[] content, DocumentType type) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable(type, "이미지 리더를 찾지 못했습니다", null);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);

                // int 곱셈은 넘칠 수 있으므로(46341×46341부터) long으로 받아 곱한다.
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > properties.maxImagePixels()) {
                    log.warn("[업로드 거부] 이미지 픽셀 수 초과: {}x{} (상한 {}픽셀)",
                            width, height, properties.maxImagePixels());
                    throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_IMAGE_TOO_LARGE);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw unreadable(type, "디코딩 결과가 없습니다", null);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof GeneralException general) {
                throw general;
            }
            throw unreadable(type, "이미지를 디코딩하지 못했습니다", e);
        }
    }

    /**
     * PDFBox로 실제로 열어본다. 세 가지를 본다 - 열리는가, 암호가 걸렸는가, 페이지 수가 상식적인가.
     *
     * <b>암호화 판정 주의</b>: 사용자 암호가 걸린 PDF는 여는 순간 예외가 나지만, 소유자 암호만 걸린
     * PDF는 빈 암호로 <b>열린다</b>. 그래서 열린 뒤에도 isEncrypted()를 한 번 더 확인해야 한다 -
     * 이런 PDF는 OCR 쪽 라이브러리에서 텍스트 추출이 막히는 경우가 있다.
     */
    private void validatePdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_PASSWORD_PROTECTED);
            }

            int pages = document.getNumberOfPages();
            if (pages < 1) {
                throw unreadable(DocumentType.PDF, "페이지가 없습니다", null);
            }
            if (pages > properties.maxPdfPages()) {
                log.warn("[업로드 거부] PDF 페이지 수 초과: {}쪽 (상한 {}쪽)", pages, properties.maxPdfPages());
                throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_TOO_MANY_PAGES);
            }
        } catch (IOException | RuntimeException e) {
            // 사용자 암호가 걸린 PDF는 InvalidPasswordException(IOException 하위)으로 여기 떨어진다.
            if (e instanceof GeneralException general) {
                throw general;
            }
            if (e instanceof org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                throw new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_PASSWORD_PROTECTED);
            }
            throw unreadable(DocumentType.PDF, "PDF를 열지 못했습니다", e);
        }
    }

    /** 원인은 로그에만 남기고 사용자에게는 형식 무관한 같은 메시지를 준다 (내부 구조를 흘리지 않는다). */
    private GeneralException unreadable(DocumentType type, String reason, Exception cause) {
        log.warn("[업로드 거부] 열 수 없는 {} 파일 - {}{}",
                type, reason, cause == null ? "" : ": " + cause.getMessage());
        return new GeneralException(BusinessRegistrationErrorCode.DOCUMENT_UNREADABLE);
    }
}
