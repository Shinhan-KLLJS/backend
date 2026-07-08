package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.dto.AuthenticatedUser;
import com.shinhan.klljs.domain.auth.principal.KakaoOAuth2UserInfo;
import com.shinhan.klljs.domain.user.entity.SocialProvider;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserSocialAccount;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.domain.user.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 카카오 로그인 성공 시 User/UserSocialAccount를 조회하거나 새로 만든다.
 * 동시 최초 가입으로 인한 UNIQUE(provider, provider_user_id) 충돌 재시도는
 * 이 서비스를 호출하는 바깥 계층(CustomOAuth2UserService)에서 새 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String DEFAULT_DISPLAY_NAME = "카카오 사용자";

    private final UserRepository userRepository;
    private final UserSocialAccountRepository socialAccountRepository;
    private final Clock clock;

    @Transactional
    // 이 카카오 회원번호로 연결된 계정이 있으면 로그인 처리, 없으면 신규 가입 처리
    public AuthenticatedUser loginOrSignUp(KakaoOAuth2UserInfo kakao) {
        return socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, kakao.providerUserId())
                .map(this::loginExistingUser)
                .orElseGet(() -> createUser(kakao));
    }

    private AuthenticatedUser loginExistingUser(UserSocialAccount socialAccount) {
        User user = socialAccount.getUser();
        reactivateOrRejectIfNeeded(user);
        user.recordLogin(LocalDateTime.now(clock));

        return new AuthenticatedUser(user.getId(), user.getStatus());
    }

    private AuthenticatedUser createUser(KakaoOAuth2UserInfo kakao) {
        LocalDateTime now = LocalDateTime.now(clock);

        User user = userRepository.save(
                User.builder()
                        .displayName(normalizeDisplayName(kakao.nickname()))
                        .email(kakao.email())
                        .profileImageUrl(kakao.profileImageUrl())
                        .status(UserStatus.ACTIVE)
                        .build()
        );
        user.recordLogin(now);

        socialAccountRepository.save(
                UserSocialAccount.builder()
                        .user(user)
                        .provider(SocialProvider.KAKAO)
                        .providerUserId(kakao.providerUserId())
                        .providerEmail(kakao.email())
                        .connectedAt(now)
                        .build()
        );

        return new AuthenticatedUser(user.getId(), user.getStatus());
    }

    /*
     * 이 코드는 @RestController 안이 아니라 Spring Security의 인증 필터 체인 안에서 실행됩니다.
     * GeneralException을 던지면 우리 프로젝트의 @RestControllerAdvice가 못 잡고 그냥 500 에러로 새 버립니다.
     * OAuth2AuthenticationException을 던져야 Spring Security가 "인증 실패"로 인식해서 failureHandler로 보내줍니다.
     *
     * WITHDRAWN은 차단하지 않는다 — 탈퇴자가 카카오로 다시 로그인하면 그 자체를 재가입 의사로 보고
     * 자동으로 ACTIVE로 되돌린 뒤 정상 로그인시킨다.
     */
    private void reactivateOrRejectIfNeeded(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new OAuth2AuthenticationException("USER_SUSPENDED");
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            user.changeStatus(UserStatus.ACTIVE);
        }
    }

    //카카오 닉네임이 없거나 공백이면 기본 이름을 사용한다. 재로그인 시 카카오 닉네임으로 덮어쓰지 않는다.
    private String normalizeDisplayName(String nickname) {
        return (nickname == null || nickname.isBlank()) ? DEFAULT_DISPLAY_NAME : nickname;
    }
}
