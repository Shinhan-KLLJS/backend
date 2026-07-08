package com.shinhan.klljs.domain.auth.principal;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoOAuth2UserInfoTest {

    @Test
    void from_parsesAllFieldsWhenPresent() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "홍길동");
        profile.put("profile_image_url", "https://example.com/profile.png");

        Map<String, Object> account = new HashMap<>();
        account.put("email", "user@example.com");
        account.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789L);
        attributes.put("kakao_account", account);

        KakaoOAuth2UserInfo result = KakaoOAuth2UserInfo.from(attributes);

        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.nickname()).isEqualTo("홍길동");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    void from_throwsWhenIdMissing() {
        Map<String, Object> attributes = new HashMap<>();

        assertThatThrownBy(() -> KakaoOAuth2UserInfo.from(attributes))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("KAKAO_PROFILE_INVALID");
    }

    @Test
    void from_treatsMissingKakaoAccountAsAllNull() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789L);

        KakaoOAuth2UserInfo result = KakaoOAuth2UserInfo.from(attributes);

        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.nickname()).isNull();
        assertThat(result.email()).isNull();
        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    void from_treatsMissingProfileAsNullNicknameAndImage() {
        Map<String, Object> account = new HashMap<>();
        account.put("email", "user@example.com");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789L);
        attributes.put("kakao_account", account);

        KakaoOAuth2UserInfo result = KakaoOAuth2UserInfo.from(attributes);

        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.nickname()).isNull();
        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    void from_ignoresMalformedKakaoAccountInsteadOfThrowing() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789L);
        attributes.put("kakao_account", "unexpected-non-map-value");

        KakaoOAuth2UserInfo result = KakaoOAuth2UserInfo.from(attributes);

        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.email()).isNull();
        assertThat(result.nickname()).isNull();
    }
}
