package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 횟수 제한. 업로드 한 번마다 CLOVA OCR이 호출되고 OCR은 <b>호출당 과금</b>이라,
 * 인증만 통과하면 무제한이면 사용자 하나가 비용을 계속 태울 수 있다.
 */
class BusinessRegistrationUploadRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Long USER_ID = 7L;

    /** 테스트 중에 시간을 마음대로 옮기기 위해 참조를 갈아끼운다. */
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    private final BusinessRegistrationUploadRateLimiter limiter =
            new BusinessRegistrationUploadRateLimiter(movableClock());

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 0; i < BusinessRegistrationUploadRateLimiter.MAX_UPLOADS_PER_WINDOW; i++) {
            int attempt = i + 1;
            assertThatCode(() -> limiter.checkAndRecord(USER_ID))
                    .as("%d번째 업로드는 허용돼야 한다", attempt)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.checkAndRecord(USER_ID))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED));
    }

    /**
     * <b>총량이 아니라 24시간 롤링 윈도우다.</b> 총량으로 두면 한 번 소진한 계정이 영원히 막힌다 -
     * 팀을 만들려는 사용자가 다시는 사업자등록증을 못 올리게 된다.
     */
    @Test
    void allowsAgainAfterTheWindowPasses() {
        exhaustLimit();

        now.set(NOW.plus(BusinessRegistrationUploadRateLimiter.WINDOW).plusSeconds(1));

        assertThatCode(() -> limiter.checkAndRecord(USER_ID)).doesNotThrowAnyException();
    }

    /** 윈도우가 부분적으로만 지났으면 그만큼만 열린다 (창을 통째로 비우지 않는다). */
    @Test
    void expiresOnlyTheUploadsThatFellOutOfTheWindow() {
        limiter.checkAndRecord(USER_ID); // t=0
        now.set(NOW.plus(Duration.ofHours(12)));
        exhaustLimitFrom(BusinessRegistrationUploadRateLimiter.MAX_UPLOADS_PER_WINDOW - 1); // t=12h에 9번 더

        // 이제 10번을 다 썼다.
        assertThatThrownBy(() -> limiter.checkAndRecord(USER_ID)).isInstanceOf(GeneralException.class);

        // t=24h+1s: t=0의 1건만 윈도우를 벗어난다 -> 딱 1번만 더 허용된다.
        now.set(NOW.plus(Duration.ofDays(1)).plusSeconds(1));
        assertThatCode(() -> limiter.checkAndRecord(USER_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAndRecord(USER_ID)).isInstanceOf(GeneralException.class);
    }

    @Test
    void countsEachUserSeparately() {
        exhaustLimit();

        assertThatCode(() -> limiter.checkAndRecord(USER_ID + 1))
                .as("다른 사용자가 남의 한도에 영향받으면 안 된다")
                .doesNotThrowAnyException();
    }

    /** 동시에 밀어넣어도 한도를 함께 뚫지 못한다 (compute로 사용자별 잠금). */
    @Test
    void concurrentUploadsCannotExceedTheLimitTogether() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                limiter.checkAndRecord(USER_ID);
                return true;
            } catch (GeneralException e) {
                return false;
            }
        };

        List<Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(attempt));
        }
        start.countDown();

        int allowed = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            } catch (ExecutionException e) {
                throw new AssertionError("예상치 못한 예외", e.getCause());
            }
        }
        executor.shutdown();

        assertThat(allowed)
                .as("동시 요청 %d건 중 정확히 %d건만 통과해야 한다",
                        threads, BusinessRegistrationUploadRateLimiter.MAX_UPLOADS_PER_WINDOW)
                .isEqualTo(BusinessRegistrationUploadRateLimiter.MAX_UPLOADS_PER_WINDOW);
    }

    private void exhaustLimit() {
        exhaustLimitFrom(BusinessRegistrationUploadRateLimiter.MAX_UPLOADS_PER_WINDOW);
    }

    private void exhaustLimitFrom(int count) {
        for (int i = 0; i < count; i++) {
            limiter.checkAndRecord(USER_ID);
        }
    }

    /** now 참조가 가리키는 시각을 그대로 돌려주는 시계. */
    private Clock movableClock() {
        return new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
