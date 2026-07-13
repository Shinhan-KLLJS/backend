package com.shinhan.klljs.domain.team.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정규화가 깨지면 정상 사업자가 반려된다 (server-verification-spec.md §1).
 * 특히 공동대표("외 N명") 처리가 빠지면 국세청이 대표자를 찾지 못해 진위확인 자체가 실패한다.
 */
class BusinessInputNormalizerTest {

    /** 미래 개업일 판정의 기준이 되는 "오늘"(KST). 실제 오늘 날짜에 의존하면 테스트가 언젠가 깨진다. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);

    /** 스펙 §1의 대표자명 정규화 예시를 그대로 옮겼다. */
    @ParameterizedTest
    @CsvSource({
            "'홍길동외 1명', 홍길동",
            "'홍길동 외 1명', 홍길동",
            "홍길동, 홍길동",
            "홍길동외1명, 홍길동",
            "'홍길동 외 12명', 홍길동"
    })
    void normalizeRepresentativeName_stripsWhitespaceAndCoRepresentativeSuffix(String raw, String expected) {
        assertThat(BusinessInputNormalizer.normalizeRepresentativeName(raw)).isEqualTo(expected);
    }

    /** OCR 결과에는 일반 공백이 아닌 전각 공백(U+3000)이 섞여 들어오는 경우가 있다. */
    @Test
    void normalizeRepresentativeName_stripsFullWidthSpace() {
        assertThat(BusinessInputNormalizer.normalizeRepresentativeName("홍길동　외　1명")).isEqualTo("홍길동");
    }

    /** "외N명"은 말미에서만 제거한다. 이름 중간의 글자를 건드리면 안 된다. */
    @Test
    void normalizeRepresentativeName_doesNotStripSuffixInTheMiddle() {
        assertThat(BusinessInputNormalizer.normalizeRepresentativeName("외1명홍길동")).isEqualTo("외1명홍길동");
    }

    @Test
    void normalize_acceptsHyphenatedNumberAndDashedDate() {
        Optional<NormalizedBusinessInput> result =
                BusinessInputNormalizer.normalize("495-92-40582", "2024-06-24", "이정현", TODAY);

        assertThat(result).isPresent();
        NormalizedBusinessInput input = result.orElseThrow();
        assertThat(input.businessNumber()).isEqualTo("4959240582");
        assertThat(input.openingDateText()).isEqualTo("20240624");
        assertThat(input.openingDate()).isEqualTo(LocalDate.of(2024, 6, 24));
        assertThat(input.representativeName()).isEqualTo("이정현");
    }

    @Test
    void normalize_stripsCoRepresentativeSuffixBeforeSendingToNts() {
        Optional<NormalizedBusinessInput> result =
                BusinessInputNormalizer.normalize("4959240582", "20240624", "홍길동외 1명", TODAY);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().representativeName()).isEqualTo("홍길동");
    }

    /** 자릿수가 맞지 않으면 국세청 API를 아예 호출하지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "", "abcdefghij"})
    void normalize_rejectsBusinessNumberThatIsNotTenDigits(String businessNumber) {
        assertThat(BusinessInputNormalizer.normalize(businessNumber, "20240624", "이정현", TODAY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024062", "202406241", ""})
    void normalize_rejectsOpeningDateThatIsNotEightDigits(String openingDate) {
        assertThat(BusinessInputNormalizer.normalize("4959240582", openingDate, "이정현", TODAY)).isEmpty();
    }

    /** 8자리여도 존재하지 않는 날짜면 DATE 컬럼에 넣을 수 없다. */
    @Test
    void normalize_rejectsNonExistentDate() {
        assertThat(BusinessInputNormalizer.normalize("4959240582", "20240631", "이정현", TODAY)).isEmpty();
    }

    @Test
    void normalize_rejectsWhenRepresentativeNameBecomesEmpty() {
        assertThat(BusinessInputNormalizer.normalize("4959240582", "20240624", "외1명", TODAY)).isEmpty();
        assertThat(BusinessInputNormalizer.normalize("4959240582", "20240624", "   ", TODAY)).isEmpty();
    }

    @Test
    void normalize_rejectsNullInputsWithoutThrowing() {
        assertThat(BusinessInputNormalizer.normalize(null, null, null, TODAY)).isEmpty();
    }

    /**
     * 아직 오지 않은 날짜는 사업자등록증에 찍혀 있을 수 없다 (team-creation-api-spec.md 5절).
     * 이걸 막지 않으면 국세청 쿼터만 쓰고 엉뚱한 사유 코드로 반려된다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"20260714", "20260801", "20990101"})
    void normalize_rejectsFutureOpeningDate(String futureDate) {
        assertThat(BusinessInputNormalizer.normalize("4959240582", futureDate, "이정현", TODAY)).isEmpty();
    }

    /** 경계값: 오늘 개업한 사업자는 통과해야 한다. 오늘까지 막아버리면 정상 사업자가 반려된다. */
    @Test
    void normalize_acceptsOpeningDateOfToday() {
        Optional<NormalizedBusinessInput> result =
                BusinessInputNormalizer.normalize("4959240582", "20260713", "이정현", TODAY);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().openingDate()).isEqualTo(TODAY);
    }
}
