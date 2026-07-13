package com.shinhan.klljs.domain.team.verification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 광고업 분류 (server-verification-spec.md §3).
 * 키워드 목록의 순서가 곧 matchedKeywords의 순서라, 순서가 바뀌면 여기서 깨진다.
 */
class AdvertisingClassifierTest {

    /**
     * 스펙 §3.2에 "실제 사업자등록증으로 확인했다"고 적힌 예시.
     * "광고"가 "광고대행"의 부분 문자열이라 함께 잡히지만 판정에는 영향이 없다.
     */
    @Test
    void classify_realCertificateExample_matchesHighConfidenceInDeclarationOrder() {
        AdvertisingClassification result = AdvertisingClassifier.classify("광고물제작", "광고대행 디자인");

        assertThat(result.matchedKeywords()).containsExactly("광고대행", "광고물", "광고");
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.HIGH);
        assertThat(result.status()).isEqualTo(AdvertisingClassificationStatus.MATCHED);
        assertThat(result.advertisingRelated()).isTrue();
        assertThat(result.reviewRequired()).isFalse();
    }

    /** 중간 신뢰도만 매칭되면 광고 관련으로 보되 수동 검토 대상이다. */
    @Test
    void classify_mediumConfidenceOnly_requiresReview() {
        AdvertisingClassification result = AdvertisingClassifier.classify("서비스업", "마케팅대행");

        assertThat(result.matchedKeywords()).containsExactly("마케팅대행", "마케팅");
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.MEDIUM);
        assertThat(result.advertisingRelated()).isTrue();
        assertThat(result.reviewRequired()).isTrue();
    }

    /** 높은 신뢰도가 하나라도 있으면 중간 신뢰도가 함께 잡혀도 high다. */
    @Test
    void classify_highBeatsMediumWhenBothMatch() {
        AdvertisingClassification result = AdvertisingClassifier.classify("광고대행", "마케팅");

        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.HIGH);
        assertThat(result.reviewRequired()).isFalse();
    }

    @Test
    void classify_noMatchWithPresentInput_isNotMatched() {
        AdvertisingClassification result = AdvertisingClassifier.classify("정보통신업", "소프트웨어 개발");

        assertThat(result.status()).isEqualTo(AdvertisingClassificationStatus.NOT_MATCHED);
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.NONE);
        assertThat(result.advertisingRelated()).isFalse();
        assertThat(result.reviewRequired()).isFalse();
        assertThat(result.isInputIncomplete()).isFalse();
        assertThat(result.matchedKeywords()).isEmpty();
    }

    /** 둘 다 비어야 unknown이다. not_matched와 달리 "판단 근거가 없다"는 뜻이라 반려가 아닌 검토로 빠진다. */
    @Test
    void classify_bothInputsMissing_isUnknown() {
        AdvertisingClassification result = AdvertisingClassifier.classify(null, "   ");

        assertThat(result.status()).isEqualTo(AdvertisingClassificationStatus.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.UNKNOWN);
        assertThat(result.isInputIncomplete()).isTrue();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.advertisingRelated()).isFalse();
    }

    /** 한쪽만 있으면 그 값만으로 분류한다 (unknown이 아니다). */
    @Test
    void classify_onlyOneInputPresent_stillClassifies() {
        assertThat(AdvertisingClassifier.classify(null, "옥외광고").confidence())
                .isEqualTo(AdvertisingConfidence.HIGH);
        assertThat(AdvertisingClassifier.classify("음식점업", null).status())
                .isEqualTo(AdvertisingClassificationStatus.NOT_MATCHED);
    }

    /** 정규화(공백 제거 + 소문자)가 양쪽에 적용되므로 띄어쓰기와 대소문자에 흔들리지 않는다. */
    @Test
    void classify_normalizesWhitespaceAndCase() {
        assertThat(AdvertisingClassifier.classify("SNS 마케팅", null).matchedKeywords())
                .containsExactly("sns마케팅", "마케팅");
        assertThat(AdvertisingClassifier.classify("옥 외 광 고", null).confidence())
                .isEqualTo(AdvertisingConfidence.HIGH);
    }

    /**
     * <b>알려진 오탐 — 스펙을 그대로 재현한 결과다.</b>
     * "광고"처럼 짧고 넓은 키워드를 부분 문자열로 검사하므로, 광고와 무관한 "관광고속버스운송업"이
     * high로 잡혀 승인된다. 스펙(§3.2)이 이 키워드 목록과 부분 문자열 매칭을 명시하고 있어
     * 기존 판정과의 동작 일치를 우선해 그대로 두되, 여기에 고정해 두어 나중에 키워드 규칙을
     * 손볼 때 이 케이스가 달라졌다는 사실이 반드시 드러나게 한다.
     */
    @Test
    void classify_knownFalsePositive_broadKeywordMatchesUnrelatedBusinessType() {
        AdvertisingClassification result = AdvertisingClassifier.classify("관광고속버스운송업", null);

        assertThat(result.matchedKeywords()).containsExactly("광고");
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.HIGH);
    }

    /**
     * <b>알려진 오탐 — 필드 경계를 넘는 매칭.</b>
     * 업태와 종목을 공백 하나로 이어 붙인 뒤 그 공백까지 제거하므로("관광" + "고물상" -> "관광고물상"),
     * 어느 필드에도 없던 "광고물"이 만들어진다. 이것도 스펙이 정한 절차(§3.1)의 직접적인 결과다.
     */
    @Test
    void classify_knownFalsePositive_crossFieldSubstringMatch() {
        AdvertisingClassification result = AdvertisingClassifier.classify("관광", "고물상");

        assertThat(result.matchedKeywords()).containsExactly("광고물", "광고");
        assertThat(result.confidence()).isEqualTo(AdvertisingConfidence.HIGH);
    }
}
