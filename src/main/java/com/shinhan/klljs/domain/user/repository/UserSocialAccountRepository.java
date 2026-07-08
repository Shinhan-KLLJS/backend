package com.shinhan.klljs.domain.user.repository;

import com.shinhan.klljs.domain.user.entity.SocialProvider;
import com.shinhan.klljs.domain.user.entity.UserSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
