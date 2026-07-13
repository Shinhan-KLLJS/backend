package com.shinhan.klljs.global.util;

import java.util.regex.Pattern;

/**
 * 사용자가 입력한 문자열을 저장하기 전에 다듬는 유틸리티.
 *
 * <b>왜 필요한가</b>: {@code @NotBlank}는 "공백뿐인 값"을 거부할 뿐, 앞뒤에 공백이 붙은 값은
 * 그대로 통과시킨다. 그래서 {@code "  루비 광고  "}가 공백째 DB에 들어가고, 이후 이름으로 검색하거나
 * 화면에 표시할 때 조용히 어긋난다. 검증(@NotBlank)과 정규화(trim)는 별개의 일이다.
 */
public final class Texts {

    /**
     * 앞뒤에서 걷어낼 공백: {@code \s}(ASCII 공백) + {@code \p{Zs}}(유니코드 공백 분리자).
     *
     * {@code String.strip()}만으로는 부족하다 - strip은 {@code Character.isWhitespace} 기준이라
     * 전각 공백(U+3000)은 지우지만 <b>NBSP(U+00A0)·NNBSP(U+202F) 같은 non-breaking 계열은
     * 남긴다</b>. OCR을 거친 값이나 웹에서 복사한 값에는 이런 문자가 섞여 들어온다.
     */
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^[\\s\\p{Zs}]+|[\\s\\p{Zs}]+$");

    private Texts() {
    }

    /** 앞뒤 공백(유니코드 공백 포함)을 제거한다. null이면 null 그대로 돌려준다 (필수 여부 판단은 이미 {@code @NotBlank}가 했다). */
    public static String trim(String raw) {
        return raw == null ? null : EDGE_WHITESPACE.matcher(raw).replaceAll("");
    }
}
