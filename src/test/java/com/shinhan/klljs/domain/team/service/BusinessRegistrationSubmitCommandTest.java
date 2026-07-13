package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청 DTO + 서명 토큰 -> 커맨드 매핑.
 *
 * <b>값의 출처가 둘로 나뉘는 것이 이 매핑의 핵심이다.</b> 사용자가 확정한 값은 요청에서,
 * 사용자가 손댈 수 없어야 하는 값(업태·종목, S3 키)은 서명 토큰에서 온다.
 * 여기서 출처를 헷갈리면 §7의 우회(업태를 "광고대행"으로 고쳐 스스로 승인)가 되살아나는데,
 * 값이 전부 String이라 잘못 매핑해도 조용히 컴파일된다. 그래서 필드별로 고정한다.
 */
class BusinessRegistrationSubmitCommandTest {

    @Test
    void of_takesUserConfirmedValuesFromRequestAndUntouchableValuesFromToken() {
        BusinessRegistrationSubmitRequest request = new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동 외 1명", "2024-06-24",
                "v1.ignored.signature");
        // 토큰에는 OCR이 읽은 원본이 서명돼 있다.
        BusinessRegistrationUploadToken token = token("team-registrations/abc.pdf", "광고물제작", "광고대행");

        BusinessRegistrationSubmitCommand command =
                BusinessRegistrationSubmitCommand.of(7L, 42L, request, token);

        assertThat(command.userId()).isEqualTo(7L);
        assertThat(command.teamId()).isEqualTo(42L);
        assertThat(command.companyName()).isEqualTo("루비 광고");
        // DB에는 서명 토큰이 아니라 토큰에서 꺼낸 순수 S3 키가 저장된다.
        assertThat(command.documentS3Key()).isEqualTo("team-registrations/abc.pdf");

        BusinessVerificationCommand verification = command.verification();
        assertThat(verification.businessNumber()).isEqualTo("495-92-40582");
        assertThat(verification.businessOpeningDate()).isEqualTo("2024-06-24");
        assertThat(verification.representativeName()).isEqualTo("홍길동 외 1명");
        // 요청이 아니라 토큰에서 온 값이다.
        assertThat(verification.businessType()).isEqualTo("광고물제작"); // 업태
        assertThat(verification.businessItem()).isEqualTo("광고대행");   // 종목
    }

    /**
     * 커맨드는 값을 가공하지 않고 그대로 옮기기만 한다. 하이픈 제거와 "외 N명" 제거는
     * 검증 단계(BusinessInputNormalizer)의 책임이라, 여기서 미리 손대면 정규화 실패 시
     * 원문을 DB에 남겨 조사하는 경로가 사라진다.
     */
    @Test
    void of_doesNotNormalizeValues() {
        BusinessRegistrationSubmitRequest request = new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동 외 1명", "2024-06-24", "v1.ignored.signature");

        BusinessVerificationCommand verification = BusinessRegistrationSubmitCommand
                .of(1L, 1L, request, token("team-registrations/a.pdf", null, null))
                .verification();

        assertThat(verification.businessNumber()).isEqualTo("495-92-40582"); // 하이픈 그대로
        assertThat(verification.representativeName()).isEqualTo("홍길동 외 1명"); // "외 1명" 그대로
    }

    /** OCR이 업태·종목을 못 읽었으면 null이 그대로 흘러가 판정 규칙 5번(review_required)에 걸린다. */
    @Test
    void of_carriesNullBusinessTypeWhenOcrCouldNotReadIt() {
        BusinessRegistrationSubmitRequest request = new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동", "2024-06-24", "v1.ignored.signature");

        BusinessVerificationCommand verification = BusinessRegistrationSubmitCommand
                .of(1L, 1L, request, token("team-registrations/a.pdf", null, null))
                .verification();

        assertThat(verification.businessType()).isNull();
        assertThat(verification.businessItem()).isNull();
    }

    private static BusinessRegistrationUploadToken token(String s3Key, String businessType, String businessItem) {
        return new BusinessRegistrationUploadToken(
                "business-registration-upload", s3Key, 7L, Long.MAX_VALUE, businessType, businessItem);
    }
}
