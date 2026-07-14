package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 토큰의 서명·검증 (docs/team-creation-api-spec.md 5절).
 *
 * 이 토큰이 두 가지를 막는다.
 * <ul>
 *   <li>남이 업로드한 사업자등록증(S3 키)으로 팀을 만드는 것</li>
 *   <li>OCR이 읽은 업태·종목을 전송 구간에서 임의 값으로 바꿔치기해 저장시키는 것</li>
 * </ul>
 * 서명 검증이 뚫리면 둘 다 되살아나므로, 우회 경로를 하나씩 고정한다.
 */
class BusinessRegistrationUploadTokenSignerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final long TTL_SECONDS = 3600;
    private static final String SECRET = "test-only-upload-token-secret-at-least-256-bits-long";
    private static final String S3_KEY = "team-registrations/3f1a9c2e.png";
    private static final Long UPLOADER_ID = 7L;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final BusinessRegistrationUploadTokenSigner signer = signerWith(SECRET, fixedClock);

    @Test
    void signThenVerify_roundTripsEveryField() {
        String token = signer.sign(S3_KEY, UPLOADER_ID, "서비스업", "광고대행");

        BusinessRegistrationUploadToken verified = signer.verify(token, UPLOADER_ID);

        assertThat(verified.s3Key()).isEqualTo(S3_KEY);
        assertThat(verified.uploaderId()).isEqualTo(UPLOADER_ID);
        assertThat(verified.businessType()).isEqualTo("서비스업");
        assertThat(verified.businessItem()).isEqualTo("광고대행");
        assertThat(verified.expiresAtEpochSecond())
                .isEqualTo(NOW.getEpochSecond() + TTL_SECONDS);
    }

    /** OCR이 업태·종목을 못 읽어도 토큰은 발급된다 (업로드 자체는 성공이다). */
    @Test
    void signThenVerify_allowsNullOcrFields() {
        BusinessRegistrationUploadToken verified =
                signer.verify(signer.sign(S3_KEY, UPLOADER_ID, null, null), UPLOADER_ID);

        assertThat(verified.businessType()).isNull();
        assertThat(verified.businessItem()).isNull();
    }

    /**
     * <b>가장 중요한 케이스.</b> payload의 업태·종목을 바꿔치기하면 서명이 깨져야 한다.
     * 여기가 뚫리면 OCR이 읽은 값 대신 아무나 지어낸 값이 DB에 저장된다.
     */
    @Test
    void verify_rejectsPayloadTamperedToClaimAdvertisingBusiness() {
        String token = signer.sign(S3_KEY, UPLOADER_ID, "음식점업", "한식");
        String[] parts = token.split("\\.");

        // 서명은 그대로 두고 payload만 광고업으로 바꿔 끼운다.
        String forgedPayload = base64Url("""
                {"purpose":"business-registration-upload","s3Key":"%s","uploaderId":%d,\
                "expiresAtEpochSecond":%d,"businessType":"서비스업","businessItem":"광고대행"}"""
                .formatted(S3_KEY, UPLOADER_ID, NOW.getEpochSecond() + TTL_SECONDS));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> signer.verify(forged, UPLOADER_ID))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
    }

    /** 남이 업로드한 문서로 팀을 만들 수 없다. */
    @Test
    void verify_rejectsTokenIssuedToAnotherUploader() {
        String token = signer.sign(S3_KEY, UPLOADER_ID, "서비스업", "광고대행");

        assertThatThrownBy(() -> signer.verify(token, UPLOADER_ID + 1))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
    }

    @Test
    void verify_rejectsExpiredToken() {
        String token = signer.sign(S3_KEY, UPLOADER_ID, "서비스업", "광고대행");

        // 만료 직후로 시계를 옮긴다. 서명은 그대로 유효하지만 시간이 지났다.
        BusinessRegistrationUploadTokenSigner later = signerWith(
                SECRET, Clock.fixed(NOW.plus(Duration.ofSeconds(TTL_SECONDS + 1)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(token, UPLOADER_ID))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
    }

    /** 다른 키로 서명된 토큰은 받지 않는다 (키 교체 후 옛 토큰도 여기서 걸린다). */
    @Test
    void verify_rejectsTokenSignedWithAnotherSecret() {
        String token = signerWith("a-completely-different-secret-value-here", fixedClock)
                .sign(S3_KEY, UPLOADER_ID, "서비스업", "광고대행");

        assertThatThrownBy(() -> signer.verify(token, UPLOADER_ID))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
    }

    /** 서버가 발급하지 않는 경로를 가리키는 토큰은 받지 않는다. */
    @Test
    void verify_rejectsKeyOutsideTheServerIssuedPrefix() {
        String token = signer.sign("other-bucket-path/secret.png", UPLOADER_ID, "서비스업", "광고대행");

        assertThatThrownBy(() -> signer.verify(token, UPLOADER_ID))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
    }

    @Test
    void verify_rejectsMalformedTokens() {
        for (String malformed : new String[] {
                "", "   ", "not-a-token", "v1.only-two-parts",
                "v2.abc.def",                       // 모르는 버전
                "v1.!!!not-base64!!!.signature",    // payload가 base64url이 아님
        }) {
            assertThatThrownBy(() -> signer.verify(malformed, UPLOADER_ID))
                    .as("malformed=%s", malformed)
                    .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));
        }
    }

    /**
     * 시크릿이 없으면 <b>서명 없이 통과시키지 않고</b> 기능 자체를 막는다.
     * 조용히 통과시키면 아무나 토큰을 위조할 수 있다.
     */
    @Test
    void signAndVerify_areRefusedWhenSecretIsMissing() {
        BusinessRegistrationUploadTokenSigner noSecret = signerWith("", fixedClock);

        assertThatThrownBy(() -> noSecret.sign(S3_KEY, UPLOADER_ID, null, null))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.UPLOAD_TOKEN_SECRET_MISSING));

        String validToken = signer.sign(S3_KEY, UPLOADER_ID, null, null);
        assertThatThrownBy(() -> noSecret.verify(validToken, UPLOADER_ID))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.UPLOAD_TOKEN_SECRET_MISSING));
    }

    private static java.util.function.Consumer<Throwable> hasErrorCode(BusinessRegistrationErrorCode expected) {
        return throwable -> {
            assertThat(throwable).isInstanceOf(GeneralException.class);
            assertThat(((GeneralException) throwable).getErrorCode()).isEqualTo(expected);
        };
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessRegistrationUploadTokenSigner signerWith(String secret, Clock clock) {
        BusinessRegistrationUploadProperties properties = new BusinessRegistrationUploadProperties(
                secret, TTL_SECONDS, "test-bucket", "ap-northeast-2",
                "team-registrations/", 10_485_760L, 30_000_000L, 5, "test-ocr-function", 30_000);
        return new BusinessRegistrationUploadTokenSigner(properties, clock);
    }
}
