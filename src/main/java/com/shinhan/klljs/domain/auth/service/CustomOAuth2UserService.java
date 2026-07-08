package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.dto.AuthenticatedUser;
import com.shinhan.klljs.domain.auth.principal.CustomOAuth2Principal;
import com.shinhan.klljs.domain.auth.principal.KakaoOAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 카카오와의 통신(토큰 교환, /v2/user/me 호출)은 DefaultOAuth2UserService에 맡기고,
 * 그 결과를 내부 사용자와 연결하는 서비스 로직만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SocialLoginService socialLoginService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"kakao".equals(registrationId)) {
            throw new OAuth2AuthenticationException("Unsupported OAuth provider");
        }

        KakaoOAuth2UserInfo kakao = KakaoOAuth2UserInfo.from(oauth2User.getAttributes());

        AuthenticatedUser user = loginOrSignUpWithRetry(kakao);

        return new CustomOAuth2Principal(user.userId(), kakao.providerUserId(), oauth2User.getAttributes());
    }

    private AuthenticatedUser loginOrSignUpWithRetry(KakaoOAuth2UserInfo kakao) {
        try {
            return socialLoginService.loginOrSignUp(kakao);
        } catch (DataIntegrityViolationException concurrentSignUp) {
            return socialLoginService.loginOrSignUp(kakao);
        }
    }
}
