package com.shinhan.klljs.domain.user.service;

import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserQueryServiceTest {

    @Autowired
    private UserQueryService userQueryService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getMe_returnsUserWhenExists() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );

        User result = userQueryService.getMe(user.getId());

        assertThat(result.getId()).isEqualTo(user.getId());
        assertThat(result.getDisplayName()).isEqualTo("철수");
    }

    @Test
    void getMe_throwsWhenUserDoesNotExist() {
        assertThatThrownBy(() -> userQueryService.getMe(999_999L))
                .isInstanceOf(GeneralException.class);
    }
}
