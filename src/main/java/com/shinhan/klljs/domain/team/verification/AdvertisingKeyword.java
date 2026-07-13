package com.shinhan.klljs.domain.team.verification;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 광고업 판단에 쓰는 키워드 목록 (server-verification-spec.md §3.2).
 *
 * {@link #ALL}의 선언 순서가 곧 검사 순서이고, 매칭된 키워드는 이 순서대로 결과 배열에 쌓인다.
 * "광고"나 "마케팅"처럼 짧고 넓은 키워드를 각 신뢰도 그룹의 뒤쪽에 두어, 더 구체적인 키워드가
 * 결과 앞에 오도록 했다. 예를 들어 "광고물제작 ... 광고대행 디자인"은 아래 순서로 매칭된다.
 *
 * <pre>
 * matchedKeywords = ["광고대행", "광고물", "광고"]
 * confidence      = high
 * </pre>
 *
 * "광고"가 "광고대행"의 부분 문자열이라 함께 잡히지만 판정에는 영향이 없다.
 * 높은 신뢰도 키워드가 하나라도 있으면 high다.
 */
public record AdvertisingKeyword(String text, AdvertisingConfidence confidence) {

    /** 본문과 키워드를 같은 규칙으로 맞추기 위한 공백 제거용 패턴. 전각 공백까지 제거한다. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);

    /** 높은 신뢰도 13개 -> 중간 신뢰도 8개 순서. 이 순서를 바꾸면 matchedKeywords의 순서가 달라진다. */
    public static final List<AdvertisingKeyword> ALL = List.of(
            high("광고대행"),
            high("광고기획"),
            high("광고제작"),
            high("광고물"),
            high("옥외광고"),
            high("전시광고"),
            high("온라인광고"),
            high("검색광고"),
            high("바이럴"),
            high("매체대행"),
            high("판촉"),
            high("판촉물"),
            high("광고"),
            medium("마케팅대행"),
            medium("홍보대행"),
            medium("프로모션"),
            medium("브랜딩"),
            medium("콘텐츠마케팅"),
            medium("sns마케팅"),
            medium("마케팅"),
            medium("홍보"));

    /**
     * 업태·종목 원문과 키워드를 같은 규칙으로 정규화한다 (§3.1): 모든 공백 제거 + 소문자 변환.
     * 소문자 변환에 Locale.ROOT를 쓰지 않으면 터키어 로케일에서 'I'가 'ı'로 바뀌어 "sns마케팅" 매칭이 깨진다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return WHITESPACE.matcher(raw).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 이 키워드를 본문과 비교할 수 있는 형태로 정규화한 값. */
    public String normalizedText() {
        return normalize(text);
    }

    private static AdvertisingKeyword high(String text) {
        return new AdvertisingKeyword(text, AdvertisingConfidence.HIGH);
    }

    private static AdvertisingKeyword medium(String text) {
        return new AdvertisingKeyword(text, AdvertisingConfidence.MEDIUM);
    }
}
