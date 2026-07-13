package com.shinhan.klljs.domain.team.verification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * data.go.kr(국세청)로 보내기 전 입력값을 정규화한다 (server-verification-spec.md §1).
 * 상태가 없는 순수 함수라 CampaignPeriodResolver처럼 static 유틸리티로 둔다.
 *
 * 형식이 맞지 않으면 {@link Optional#empty()}를 돌려주고, 호출자는 국세청 API를 호출하지 않은 채
 * review_required(VALIDATION_INPUT_INCOMPLETE)로 판정한다. 형식 오류를 API에 그대로 넘기면
 * 쿼터만 소모하고 결과도 신뢰할 수 없기 때문이다.
 */
public final class BusinessInputNormalizer {

    private static final int BUSINESS_NUMBER_LENGTH = 10;
    private static final int OPENING_DATE_LENGTH = 8;

    private static final Pattern NON_DIGIT = Pattern.compile("\\D");

    /**
     * UNICODE_CHARACTER_CLASS를 켜야 전각 공백(U+3000)이나 NBSP(U+00A0)까지 잡힌다.
     * 사업자등록증 OCR 결과에는 일반 공백이 아닌 문자가 섞여 들어오는 경우가 있다.
     */
    private static final Pattern WHITESPACE = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * 사업자등록증은 공동대표를 "외 N명"으로 표기하지만 국세청 진위확인은 대표자 한 명만 받는다.
     * 이 정규화가 없으면 공동대표 사업자는 진위확인에 실패한다.
     * 공백을 먼저 모두 지우므로 "홍길동 외 1명"과 "홍길동외1명"이 같은 문자열이 되어 한 패턴으로 처리된다.
     */
    private static final Pattern CO_REPRESENTATIVE_SUFFIX = Pattern.compile("외\\d+명$");

    private BusinessInputNormalizer() {
    }

    /**
     * 세 필드를 모두 정규화하고, 하나라도 형식을 만족하지 못하면 빈 값을 반환한다.
     *
     * @param businessNumber     사업자등록번호. 하이픈 등 숫자가 아닌 문자를 제거하고 10자리인지 확인한다
     * @param openingDate        개업일자. 숫자가 아닌 문자를 제거하고 8자리 + 실재하는 날짜 + 미래가 아닌지 확인한다
     * @param representativeName 대표자명. 공백을 모두 제거하고 말미의 "외N명"을 떼어낸다
     * @param todayKst           오늘(KST). 개업일자가 미래인지 판단하는 기준이다
     */
    public static Optional<NormalizedBusinessInput> normalize(
            String businessNumber, String openingDate, String representativeName, LocalDate todayKst) {

        String number = digitsOnly(businessNumber);
        String dateText = digitsOnly(openingDate);
        String name = normalizeRepresentativeName(representativeName);

        if (number.length() != BUSINESS_NUMBER_LENGTH || dateText.length() != OPENING_DATE_LENGTH || name.isEmpty()) {
            return Optional.empty();
        }

        // 길이가 8이어도 20240631처럼 존재하지 않는 날짜일 수 있다. DATE 컬럼에 넣어야 하므로 여기서 걸러낸다.
        LocalDate parsedOpeningDate = parseBasicIsoDate(dateText);
        if (parsedOpeningDate == null) {
            return Optional.empty();
        }

        // 아직 오지 않은 개업일은 사업자등록증에 찍혀 있을 수 없다 (team-creation-api-spec.md 5절).
        // 국세청에 물어봐도 어차피 진위확인에 실패하므로, 쿼터를 쓰기 전에 여기서 막는다.
        // 기준은 KST다 - 개업일자는 한국 행정 문서의 날짜이므로 서버의 UTC 날짜로 판단하면
        // 한국 시각 09시 이전에 오늘 개업한 사업자가 "미래"로 잘못 걸린다.
        if (parsedOpeningDate.isAfter(todayKst)) {
            return Optional.empty();
        }

        return Optional.of(new NormalizedBusinessInput(number, dateText, parsedOpeningDate, name));
    }

    /**
     * 모든 공백을 제거한 뒤 말미의 "외N명"을 제거한다.
     * 예) "홍길동외 1명" -> "홍길동", "홍길동 외 1명" -> "홍길동", "홍길동" -> "홍길동"
     */
    public static String normalizeRepresentativeName(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutWhitespace = WHITESPACE.matcher(raw).replaceAll("");
        return CO_REPRESENTATIVE_SUFFIX.matcher(withoutWhitespace).replaceAll("");
    }

    /** 숫자가 아닌 문자를 모두 제거한다. null이면 빈 문자열. */
    public static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        return NON_DIGIT.matcher(raw).replaceAll("");
    }

    /** {@code YYYYMMDD} 문자열을 날짜로 파싱한다. 실재하지 않는 날짜면 null. */
    private static LocalDate parseBasicIsoDate(String yyyyMMdd) {
        try {
            return LocalDate.parse(yyyyMMdd, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
