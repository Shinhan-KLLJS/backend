package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.util.TokenHasher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 두 스레드가 같은 Refresh Token으로 동시에 rotate()를 호출했을 때
 * SELECT ... FOR UPDATE 잠금이 정말로 하나만 통과시키는지 검증한다.
 * H2는 MySQL과 잠금 동작이 완전히 같다는 보장이 없어서 반드시 실제 MySQL로 검증한다.
 * 기본 './gradlew test'에서는 제외되고, MySQL 컨테이너가 준비된 상태에서
 * './gradlew mysqlConcurrencyTest'로만 실행된다.
 */
@Tag("mysql-concurrency")
@SpringBootTest
class RefreshTokenConcurrencyMySqlTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://localhost:13308/klljs_concurrency_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "root");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rotate_underConcurrentRequests_onlyOneWinsAndWholeFamilyEndsRevoked() throws Exception {
        User user = userRepository.save(User.builder().displayName("동시성테스트").status(UserStatus.ACTIVE).build());

        String rawToken = refreshTokenService.issue(user.getId());
        byte[] familyId = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow()
                .getTokenFamilyId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Callable<String> attemptRotate = () -> {
            startLatch.await();
            return refreshTokenService.rotate(rawToken);
        };

        Future<String> first = executor.submit(attemptRotate);
        Future<String> second = executor.submit(attemptRotate);
        startLatch.countDown();

        int successCount = 0;
        int failureCount = 0;
        for (Future<String> future : List.of(first, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException expected) {
                failureCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        // 진 쪽은 "이미 폐기된 토큰 재사용"으로 해석되어 family 전체가 폐기된다 —
        // 이긴 쪽이 방금 발급받은 새 토큰까지 함께 무효화된다는 뜻이다.
        List<AuthRefreshToken> familyTokens = refreshTokenRepository.findByTokenFamilyId(familyId);
        assertThat(familyTokens).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }
}
