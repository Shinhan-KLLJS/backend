package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationUploadResponse;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 사업자등록증 업로드 (docs/team-creation-api-spec.md 4절).
 * S3와 Lambda만 대역으로 바꾸고 파일 검증·토큰 발급은 실제로 동작시킨다.
 *
 * local-test-data를 끄는 이유: 테스트 컨텍스트들이 같은 인메모리 H2를 공유하는데, 이 클래스의
 * mock 조합은 새 컨텍스트를 만들므로 켜두면 seed 초기화가 다시 돌아 팀/멤버 행을 남기고,
 * 다른 컨텍스트 테스트의 정리(deleteAll)가 FK 위반으로 깨진다.
 */
@SpringBootTest(properties = "app.local-test-data.enabled=false")
class BusinessRegistrationUploadServiceTest {

    private static final Long USER_ID = 7L;
    private static final String S3_KEY = "team-registrations/3f1a9c2e.png";

    @Autowired
    private BusinessRegistrationUploadService uploadService;

    @Autowired
    private BusinessRegistrationUploadTokenSigner tokenSigner;

    @MockitoBean
    private BusinessRegistrationDocumentStorage documentStorage;

    @MockitoBean
    private BusinessRegistrationOcrClient ocrClient;

    /**
     * 여기서 볼 것은 업로드 로직이지 횟수 제한이 아니다.
     * 진짜 리미터를 쓰면 테스트가 쌓일수록 한도(10회)에 걸려 엉뚱하게 깨진다 -
     * 제한 자체는 BusinessRegistrationUploadRateLimiterTest와 컨트롤러 테스트(429)가 검증한다.
     */
    @MockitoBean
    private BusinessRegistrationUploadRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        given(documentStorage.store(any(), any())).willReturn(S3_KEY);
    }

    @Test
    void upload_returnsOcrFieldsAndASignedTokenThatCarriesTheRealS3Key() {
        given(ocrClient.extract(S3_KEY)).willReturn(new OcrResult(
                "신한 KLLJS", "이정현", "4959240582", "2024-06-24", "서비스업", "광고대행"));

        BusinessRegistrationUploadResponse response = uploadService.upload(USER_ID, pngFile());

        assertThat(response.ocrResult().companyName()).isEqualTo("신한 KLLJS");
        assertThat(response.ocrResult().businessNumber()).isEqualTo("4959240582");
        assertThat(response.ocrResult().businessOpeningDate()).isEqualTo("2024-06-24");

        // 응답의 documentStorageKey는 원본 S3 키가 아니라 서명 토큰이다.
        assertThat(response.documentStorageKey()).isNotEqualTo(S3_KEY).startsWith("v1.");
        assertThat(tokenSigner.verify(response.documentStorageKey(), USER_ID).s3Key()).isEqualTo(S3_KEY);
    }

    /**
     * <b>업태·종목은 응답에 노출하지 않는다.</b> 화면에 보여주면 사용자가 "광고대행"으로 고쳐 제출해
     * 스스로를 승인시킬 수 있다 (server-verification-spec.md §7). 서명 토큰 안으로만 나른다.
     */
    @Test
    void upload_hidesBusinessTypeFromTheResponseButSignsItIntoTheToken() {
        given(ocrClient.extract(S3_KEY)).willReturn(new OcrResult(
                "우리 식당", "홍길동", "4959240582", "2024-06-24", "음식점업", "한식"));

        BusinessRegistrationUploadResponse response = uploadService.upload(USER_ID, pngFile());

        // 응답 어디에도 업태·종목이 없다 (OcrFields에 필드 자체가 없다).
        assertThat(response.ocrResult()).isEqualTo(new BusinessRegistrationUploadResponse.OcrFields(
                "우리 식당", "홍길동", "4959240582", "2024-06-24"));

        // 대신 토큰 안에 OCR 원본 그대로 서명돼 있다 - 사용자가 바꿀 수 없다.
        BusinessRegistrationUploadToken token =
                tokenSigner.verify(response.documentStorageKey(), USER_ID);
        assertThat(token.businessType()).isEqualTo("음식점업");
        assertThat(token.businessItem()).isEqualTo("한식");
    }

    /** OCR이 통째로 실패해도 업로드는 성공이다. 사용자가 값을 직접 입력하면 된다. */
    @Test
    void upload_succeedsWithNullFieldsWhenOcrFails() {
        given(ocrClient.extract(S3_KEY)).willReturn(OcrResult.empty());

        BusinessRegistrationUploadResponse response = uploadService.upload(USER_ID, pngFile());

        assertThat(response.documentStorageKey()).isNotBlank();
        assertThat(response.ocrResult().companyName()).isNull();
        assertThat(response.ocrResult().businessNumber()).isNull();
    }

    /**
     * 확장자만 바꾼 실행 파일은 거부한다. 매직바이트가 실제 내용을 말해준다.
     * <b>S3에 저장하기 전에 막아야 한다</b> - 통과시키면 우리 버킷이 악성코드 배포처가 된다.
     */
    @Test
    void upload_rejectsAnExecutableRenamedToPngBeforeTouchingStorage() {
        byte[] windowsExecutable = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MultipartFile disguised = new MockMultipartFile(
                "file", "사업자등록증.png", "image/png", windowsExecutable);

        assertThatThrownBy(() -> uploadService.upload(USER_ID, disguised))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.UNSUPPORTED_DOCUMENT_TYPE));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    @Test
    void upload_rejectsAnEmptyFile() {
        MultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> uploadService.upload(USER_ID, empty))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_MISSING));

        verify(documentStorage, never()).store(any(), any());
    }

    /**
     * 용량 초과는 400(BUSINESS_400_003)이다 - team-creation-api-spec.md 4절.
     *
     * 이 경로는 오랫동안 도달할 수 없었다. 서블릿 multipart 상한이 서비스 상한과 똑같은 10MB라서
     * 큰 파일은 컨트롤러에 닿기도 전에 413으로 잘렸고, 이 검사와 에러 코드는 죽은 코드였다.
     * multipart 상한을 20MB로 벌려 두 경계를 분리했으므로, 이제 10MB 초과는 여기서 400으로 걸린다.
     */
    @Test
    void upload_rejectsFileOverTenMegabytesWithBadRequestNotPayloadTooLarge() {
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, 0, tooLarge, 0, 4);
        MultipartFile oversized = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

        assertThatThrownBy(() -> uploadService.upload(USER_ID, oversized))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> {
                    BusinessRegistrationErrorCode code =
                            (BusinessRegistrationErrorCode) ((GeneralException) ex).getErrorCode();
                    assertThat(code).isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_TOO_LARGE);
                    assertThat(code.getHttpStatus().value()).isEqualTo(400);
                });

        verify(documentStorage, never()).store(any(), any());
    }

    @Test
    void upload_acceptsPdf() {
        given(ocrClient.extract(S3_KEY)).willReturn(OcrResult.empty());
        MultipartFile pdf = new MockMultipartFile(
                "file", "reg.pdf", "application/pdf", DocumentFixtures.pdf(1));

        assertThat(uploadService.upload(USER_ID, pdf).documentStorageKey()).isNotBlank();
    }

    /**
     * 시그니처는 진짜인데 내용이 깨진 이미지는 거부한다 (team-creation-api-spec.md 4절).
     *
     * 매직바이트 검사만으로는 이걸 못 잡는다. 통과시키면 S3에 저장되고 <b>OCR Lambda까지 호출된 뒤에야</b>
     * (호출당 과금) 아무것도 못 읽었다는 결과가 돌아온다. 저장 전에 디코딩해서 막는다.
     */
    @Test
    void upload_rejectsCorruptedImageBeforeTouchingStorageOrOcr() {
        MultipartFile corrupted = new MockMultipartFile(
                "file", "사업자등록증.png", "image/png", DocumentFixtures.corruptedPng());

        assertThatThrownBy(() -> uploadService.upload(USER_ID, corrupted))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_UNREADABLE));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    /**
     * <b>픽셀 폭탄(decompression bomb)은 디코딩 전에 걸러야 한다.</b>
     *
     * 파일 크기 제한(10MB)은 인코딩된 바이트만 본다. 고압축 PNG는 몇 십 바이트로도 25억 픽셀을
     * 선언할 수 있고, 그대로 디코딩하면 약 10GB 힙이 필요해 단일 EC2 인스턴스가 OOM으로 내려간다.
     * 헤더의 가로×세로만 읽어 거부해야 한다 - 이 테스트가 통과한다는 것 자체가
     * 디코딩 없이 걸러냈다는 뜻이다(디코딩했다면 테스트 JVM이 죽는다).
     */
    @Test
    void upload_rejectsImageDeclaringHugeDimensionsWithoutDecodingIt() {
        MultipartFile bomb = new MockMultipartFile(
                "file", "bomb.png", "image/png", DocumentFixtures.hugeDimensionPng());

        assertThatThrownBy(() -> uploadService.upload(USER_ID, bomb))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_IMAGE_TOO_LARGE));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    /** "%PDF-" 헤더만 붙인 파일도 마찬가지다. 실제로 파싱해봐야 걸린다. */
    @Test
    void upload_rejectsFilePretendingToBePdf() {
        MultipartFile fake = new MockMultipartFile(
                "file", "reg.pdf", "application/pdf", DocumentFixtures.fakePdf());

        assertThatThrownBy(() -> uploadService.upload(USER_ID, fake))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_UNREADABLE));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    /** 암호가 걸린 PDF는 OCR이 읽을 수 없다. 저장하기 전에 막고 사용자에게 안내한다. */
    @Test
    void upload_rejectsPasswordProtectedPdf() {
        MultipartFile locked = new MockMultipartFile(
                "file", "reg.pdf", "application/pdf", DocumentFixtures.passwordProtectedPdf());

        assertThatThrownBy(() -> uploadService.upload(USER_ID, locked))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_PASSWORD_PROTECTED));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    /**
     * 사업자등록증은 한 장짜리 문서다. OCR은 페이지 수에 비례해 과금되므로,
     * 수백 쪽짜리 PDF를 그대로 넘기면 비용을 태우는 통로가 된다. 상한은 5쪽이다.
     */
    @Test
    void upload_rejectsPdfWithTooManyPages() {
        MultipartFile many = new MockMultipartFile(
                "file", "reg.pdf", "application/pdf", DocumentFixtures.pdf(6));

        assertThatThrownBy(() -> uploadService.upload(USER_ID, many))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.DOCUMENT_TOO_MANY_PAGES));

        verify(documentStorage, never()).store(any(), any());
        verify(ocrClient, never()).extract(anyString());
    }

    /** 경계값: 상한(5쪽)까지는 통과해야 한다. 스캔본이 여러 장으로 나뉘는 경우가 있다. */
    @Test
    void upload_acceptsPdfAtThePageLimit() {
        given(ocrClient.extract(S3_KEY)).willReturn(OcrResult.empty());
        MultipartFile fivePages = new MockMultipartFile(
                "file", "reg.pdf", "application/pdf", DocumentFixtures.pdf(5));

        assertThat(uploadService.upload(USER_ID, fivePages).documentStorageKey()).isNotBlank();
    }

    /** JPEG도 디코딩해서 통과시킨다 (허용 형식 세 가지 중 이미지 둘 다 확인). */
    @Test
    void upload_acceptsJpeg() {
        given(ocrClient.extract(S3_KEY)).willReturn(OcrResult.empty());
        MultipartFile jpeg = new MockMultipartFile(
                "file", "reg.jpg", "image/jpeg", DocumentFixtures.jpeg());

        assertThat(uploadService.upload(USER_ID, jpeg).documentStorageKey()).isNotBlank();
    }

    /** 실제로 디코딩되는 PNG. 서버가 이미지를 열어보므로 헤더만 흉내 낸 배열로는 통과하지 못한다. */
    private static MultipartFile pngFile() {
        return new MockMultipartFile("file", "사업자등록증.png", "image/png", DocumentFixtures.png());
    }
}
