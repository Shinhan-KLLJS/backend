package com.shinhan.klljs.domain.team.verification;

import java.util.List;

/**
 * 광고업 분류 결과 (server-verification-spec.md §3.3).
 *
 * <table>
 *   <caption>판정표</caption>
 *   <tr><th>조건</th><th>status</th><th>confidence</th><th>reviewRequired</th><th>advertisingRelated</th></tr>
 *   <tr><td>높은 신뢰도 매칭</td><td>matched</td><td>high</td><td>false</td><td>true</td></tr>
 *   <tr><td>중간 신뢰도만 매칭</td><td>matched</td><td>medium</td><td><b>true</b></td><td>true</td></tr>
 *   <tr><td>매칭 없음 + 업태·종목 누락</td><td>unknown</td><td>unknown</td><td>true</td><td>false</td></tr>
 *   <tr><td>매칭 없음 + 업태·종목 있음</td><td>not_matched</td><td>none</td><td>false</td><td>false</td></tr>
 * </table>
 *
 * @param matchedKeywords 매칭된 키워드 전부. {@link AdvertisingKeyword#ALL} 선언 순서를 그대로 따른다
 */
public record AdvertisingClassification(
        AdvertisingClassificationStatus status,
        AdvertisingConfidence confidence,
        boolean reviewRequired,
        boolean advertisingRelated,
        List<String> matchedKeywords
) {

    public AdvertisingClassification {
        matchedKeywords = List.copyOf(matchedKeywords);
    }

    /** 높은 신뢰도 키워드가 하나라도 매칭됐다. 나머지 조건만 통과하면 바로 승인된다. */
    static AdvertisingClassification high(List<String> matchedKeywords) {
        return new AdvertisingClassification(
                AdvertisingClassificationStatus.MATCHED, AdvertisingConfidence.HIGH, false, true, matchedKeywords);
    }

    /**
     * 중간 신뢰도 키워드만 매칭됐다. 광고 관련으로 보되 수동 검토 대상이다.
     * "마케팅"이나 "홍보"만으로는 옥외광고 매체를 집행할 사업자인지 확정할 수 없기 때문이다.
     */
    static AdvertisingClassification medium(List<String> matchedKeywords) {
        return new AdvertisingClassification(
                AdvertisingClassificationStatus.MATCHED, AdvertisingConfidence.MEDIUM, true, true, matchedKeywords);
    }

    /** 업태·종목이 모두 비어 있어 판단할 근거가 없다. */
    static AdvertisingClassification unknown() {
        return new AdvertisingClassification(
                AdvertisingClassificationStatus.UNKNOWN, AdvertisingConfidence.UNKNOWN, true, false, List.of());
    }

    /** 업태·종목은 있으나 광고 관련 키워드가 하나도 없다. */
    static AdvertisingClassification notMatched() {
        return new AdvertisingClassification(
                AdvertisingClassificationStatus.NOT_MATCHED, AdvertisingConfidence.NONE, false, false, List.of());
    }

    /**
     * 최종 판정 §4의 5번 규칙(업태·종목 누락 -> review_required)에 해당하는지.
     * 이 규칙이 6번(광고업 아님 -> rejected)보다 먼저 검사되므로, 근거가 없는 경우와
     * 근거가 있는데 광고업이 아닌 경우가 구분된다.
     */
    public boolean isInputIncomplete() {
        return status == AdvertisingClassificationStatus.UNKNOWN;
    }
}
