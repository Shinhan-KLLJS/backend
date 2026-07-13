package com.shinhan.klljs.domain.team.verification;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 업태·종목으로 광고 관련 사업자인지 분류한다 (server-verification-spec.md §3).
 * 입력은 사용자가 확정한 두 값뿐이며, data.go.kr은 이 값을 확인해주지 않는다.
 *
 * 상태가 없는 순수 함수라 Spring 빈이 아니라 static 유틸리티로 둔다.
 */
public final class AdvertisingClassifier {

    private AdvertisingClassifier() {
    }

    /**
     * @param businessType 업태. 없으면 null/빈 문자열
     * @param businessItem 종목. 없으면 null/빈 문자열
     */
    public static AdvertisingClassification classify(String businessType, String businessItem) {
        // 1. 두 필드 중 값이 있는 것만 모아 공백 하나로 이어 붙인다.
        String combined = Stream.of(businessType, businessItem)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));

        // 둘 다 비어 있으면 판단 근거 자체가 없다 (matched/not_matched와 구분되는 unknown).
        if (combined.isBlank()) {
            return AdvertisingClassification.unknown();
        }

        // 2~3. 본문과 키워드를 같은 방식으로 정규화한 뒤 부분 문자열 포함 여부로 검사한다.
        String haystack = AdvertisingKeyword.normalize(combined);
        List<AdvertisingKeyword> matched = AdvertisingKeyword.ALL.stream()
                .filter(keyword -> haystack.contains(keyword.normalizedText()))
                .toList();

        if (matched.isEmpty()) {
            return AdvertisingClassification.notMatched();
        }

        List<String> matchedTexts = matched.stream().map(AdvertisingKeyword::text).toList();

        // 높은 신뢰도 키워드가 하나라도 있으면 high다. "광고"가 "광고대행"과 함께 잡혀도 결과는 같다.
        boolean hasHighConfidence = matched.stream()
                .anyMatch(keyword -> keyword.confidence() == AdvertisingConfidence.HIGH);

        return hasHighConfidence
                ? AdvertisingClassification.high(matchedTexts)
                : AdvertisingClassification.medium(matchedTexts);
    }
}
