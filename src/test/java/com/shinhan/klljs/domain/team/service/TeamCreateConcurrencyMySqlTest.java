package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
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
 * 실제 MySQL에서 같은 사용자의 동시 팀 생성 요청을 검증한다.
 * H2는 InnoDB의 잠금·격리 동작을 재현하지 못하므로 mysqlConcurrencyTest 태스크에서만 실행한다.
 */
@Tag("mysql-concurrency")
@SpringBootTest(properties = "app.local-test-data.enabled=false")
class TeamCreateConcurrencyMySqlTest {

    private static final String S3_KEY = "team-registrations/concurrent-document.pdf";

    @Autowired
    private TeamCreateService teamCreateService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://localhost:13308/klljs_concurrency_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "root");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void create_underConcurrentRequests_createsOnlyOneTeam() throws Exception {
        User user = userRepository.save(
                User.builder().displayName("동시성테스트").status(UserStatus.ACTIVE).build());
        TeamCreateRequest request = requestFor(user.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Callable<TeamCreateResponse> createTeam = () -> {
            startLatch.await();
            return teamCreateService.create(user.getId(), request);
        };

        try {
            List<Future<TeamCreateResponse>> attempts = List.of(
                    executor.submit(createTeam), executor.submit(createTeam));
            startLatch.countDown();

            List<AttemptResult> results = attempts.stream()
                    .map(this::awaitResult)
                    .toList();

            assertThat(results).filteredOn(AttemptResult::succeeded).hasSize(1);
            assertThat(results).filteredOn(result -> !result.succeeded())
                    .extracting(AttemptResult::failure)
                    .allSatisfy(this::assertAlreadyHasTeam);
            assertThat(teamRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 두 요청이 함께 출발하되, 결과 수집은 테스트 스레드에서 순서대로 한다. */
    private AttemptResult awaitResult(Future<TeamCreateResponse> attempt) {
        try {
            return AttemptResult.success(attempt.get(10, TimeUnit.SECONDS));
        } catch (ExecutionException exception) {
            return AttemptResult.failure(exception.getCause());
        } catch (Exception exception) {
            throw new AssertionError("동시 팀 생성 요청이 제시간에 끝나지 않았습니다.", exception);
        }
    }

    /** 실패한 요청은 잠금 해제 뒤 활성 팀을 발견해 정해진 도메인 오류를 반환해야 한다. */
    private void assertAlreadyHasTeam(Throwable failure) {
        assertThat(failure).isInstanceOf(GeneralException.class);
        assertThat(((GeneralException) failure).getErrorCode())
                .isEqualTo(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM);
    }

    private TeamCreateRequest requestFor(Long userId) {
        String token = uploadTokenSigner.sign(S3_KEY, userId, "광고물제작", "광고대행");
        return new TeamCreateRequest(
                "동시성 검증팀", "루비 광고", "홍길동", "495-92-40582", "2024-06-24", token);
    }

    /** 성공 응답은 필요 없고, 성공·실패 건수와 실패 원인만 판정한다. */
    private record AttemptResult(boolean succeeded, Throwable failure) {
        static AttemptResult success(TeamCreateResponse ignored) {
            return new AttemptResult(true, null);
        }

        static AttemptResult failure(Throwable failure) {
            return new AttemptResult(false, failure);
        }
    }
}
