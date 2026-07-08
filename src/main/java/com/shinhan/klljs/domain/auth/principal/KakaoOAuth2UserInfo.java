package com.shinhan.klljs.domain.auth.principal;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

/**
 * 카카오 /v2/user/me 응답(OAuth2User.getAttributes())을 내부에서 쓰기 좋은 형태로 변환한다.
 * email/nickname/profileImageUrl은 사용자가 항목 제공에 동의하지 않으면 응답에서 통째로 빠질 수 있다.
 */
public record KakaoOAuth2UserInfo(
        String providerUserId,
        String nickname,
        String email,
        String profileImageUrl
) {

    public static KakaoOAuth2UserInfo from(Map<String, Object> attributes) {
        Object rawId = attributes.get("id");
        if (rawId == null) {
            throw new OAuth2AuthenticationException("KAKAO_PROFILE_INVALID");
        }

        Map<String, Object> account = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = account == null ? null : asMap(account.get("profile"));

        return new KakaoOAuth2UserInfo(
                String.valueOf(rawId),
                profile == null ? null : (String) profile.get("nickname"),
                account == null ? null : (String) account.get("email"),
                profile == null ? null : (String) profile.get("profile_image_url")
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }
}
