package com.shinhan.klljs.global.util;

/**
 * 사용자가 입력한 문자열을 저장하기 전에 다듬는 유틸리티.
 *
 * <b>왜 필요한가</b>: {@code @NotBlank}는 "공백뿐인 값"을 거부할 뿐, 앞뒤에 공백이 붙은 값은
 * 그대로 통과시킨다. 그래서 {@code "  루비 광고  "}가 공백째 DB에 들어가고, 이후 이름으로 검색하거나
 * 화면에 표시할 때 조용히 어긋난다. 검증(@NotBlank)과 정규화(trim)는 별개의 일이다.
 */
public final class Texts {

    private Texts() {
    }

    /**
     * 앞뒤 공백을 제거한다. null이면 null 그대로 돌려준다 (필수 여부 판단은 이미 {@code @NotBlank}가 했다).
     *
     * {@code trim()}이 아니라 {@code strip()}을 쓰는 이유: {@code trim()}은 U+0020 이하의 문자만
     * 지워서 전각 공백(U+3000)이나 NBSP(U+00A0)를 남긴다. OCR을 거친 값에는 이런 문자가 섞여 들어온다.
     */
    public static String trim(String raw) {
        return raw == null ? null : raw.strip();
    }
}
