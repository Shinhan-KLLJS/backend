package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.dto.AuthenticatedUser;
import com.shinhan.klljs.domain.auth.principal.KakaoOAuth2UserInfo;
import com.shinhan.klljs.domain.user.entity.SocialProvider;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserSocialAccount;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.domain.user.repository.UserSocialAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SocialLoginServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSocialAccountRepository socialAccountRepository;

    @Autowired
    private EntityManager entityManager;

    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(userRepository, socialAccountRepository, FIXED_CLOCK);
    }

    @Test
    void loginOrSignUp_createsNewUserAndSocialAccountWhenNoneExists() {
        KakaoOAuth2UserInfo kakao = new KakaoOAuth2UserInfo(
                "1001", "홍길동", "hong@example.com", "https://example.com/hong.png"
        );

        AuthenticatedUser result = service.loginOrSignUp(kakao);
        entityManager.flush();

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);

        User savedUser = userRepository.findById(result.userId()).orElseThrow();
        assertThat(savedUser.getDisplayName()).isEqualTo("홍길동");
        assertThat(savedUser.getEmail()).isEqualTo("hong@example.com");

        UserSocialAccount savedAccount = socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, "1001")
                .orElseThrow();
        assertThat(savedAccount.getUser().getId()).isEqualTo(result.userId());
    }

    @Test
    void loginOrSignUp_blankNicknameFallsBackToDefaultDisplayName() {
        KakaoOAuth2UserInfo kakao = new KakaoOAuth2UserInfo("1002", "   ", null, null);

        AuthenticatedUser result = service.loginOrSignUp(kakao);

        User savedUser = userRepository.findById(result.userId()).orElseThrow();
        assertThat(savedUser.getDisplayName()).isEqualTo("카카오 사용자");
    }

    @Test
    void loginOrSignUp_recordsLoginForExistingUserWithoutOverwritingDisplayName() {
        User user = persistUser("기존닉네임", UserStatus.ACTIVE);
        persistSocialAccount(user, "1003");

        KakaoOAuth2UserInfo kakao = new KakaoOAuth2UserInfo("1003", "새로바뀐카카오닉네임", null, null);

        AuthenticatedUser result = service.loginOrSignUp(kakao);

        assertThat(result.userId()).isEqualTo(user.getId());
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("기존닉네임");
        assertThat(reloaded.getLastLoginAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    void loginOrSignUp_throwsForSuspendedUser() {
        User user = persistUser("정지됨", UserStatus.SUSPENDED);
        persistSocialAccount(user, "1004");

        KakaoOAuth2UserInfo kakao = new KakaoOAuth2UserInfo("1004", "정지됨", null, null);

        assertThatThrownBy(() -> service.loginOrSignUp(kakao))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("USER_SUSPENDED");
    }

    @Test
    void loginOrSignUp_reactivatesWithdrawnUserAndLogsIn() {
        User user = persistUser("탈퇴함", UserStatus.WITHDRAWN);
        persistSocialAccount(user, "1005");

        KakaoOAuth2UserInfo kakao = new KakaoOAuth2UserInfo("1005", "탈퇴함", null, null);

        AuthenticatedUser result = service.loginOrSignUp(kakao);

        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void loginOrSignUp_doesNotMergeUsersBySharedEmail() {
        KakaoOAuth2UserInfo firstKakao = new KakaoOAuth2UserInfo("2001", "철수", "shared@example.com", null);
        KakaoOAuth2UserInfo secondKakao = new KakaoOAuth2UserInfo("2002", "영희", "shared@example.com", null);

        AuthenticatedUser first = service.loginOrSignUp(firstKakao);
        AuthenticatedUser second = service.loginOrSignUp(secondKakao);

        assertThat(first.userId()).isNotEqualTo(second.userId());
        assertThat(userRepository.findAllById(List.of(first.userId(), second.userId())))
                .extracting(User::getEmail)
                .containsExactly("shared@example.com", "shared@example.com");
    }

    @Test
    void socialAccountUniqueConstraint_rejectsDuplicateProviderAndProviderUserId() {
        User firstUser = persistUser("A", UserStatus.ACTIVE);
        persistSocialAccount(firstUser, "3001");
        entityManager.flush();

        User secondUser = persistUser("B", UserStatus.ACTIVE);
        UserSocialAccount duplicate = UserSocialAccount.builder()
                .user(secondUser)
                .provider(SocialProvider.KAKAO)
                .providerUserId("3001")
                .connectedAt(LocalDateTime.now(FIXED_CLOCK))
                .build();

        assertThatThrownBy(() -> socialAccountRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String displayName, UserStatus status) {
        User user = User.builder()
                .displayName(displayName)
                .status(status)
                .build();
        entityManager.persist(user);
        return user;
    }

    private void persistSocialAccount(User user, String providerUserId) {
        UserSocialAccount account = UserSocialAccount.builder()
                .user(user)
                .provider(SocialProvider.KAKAO)
                .providerUserId(providerUserId)
                .connectedAt(LocalDateTime.now(FIXED_CLOCK))
                .build();
        entityManager.persist(account);
    }
}
