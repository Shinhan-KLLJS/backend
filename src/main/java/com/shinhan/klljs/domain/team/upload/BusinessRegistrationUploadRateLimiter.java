package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사업자등록증 업로드 횟수를 계정당 제한한다.
 *
 * <h3>왜 필요한가</h3>
 * 업로드 한 번마다 S3 저장과 <b>CLOVA OCR 호출</b>이 일어나고, OCR은 호출당 과금이다.
 * 인증만 통과하면 무제한으로 부를 수 있으면 로그인한 사용자 하나가 비용을 계속 태울 수 있다.
 * (설계 문서가 "API Gateway를 열어두면 누구나 OCR을 호출해 비용을 태울 수 있다"를 SDK invoke를
 * 택한 근거로 들었는데, 정작 우리 엔드포인트에는 제한이 없었다.)
 *
 * <h3>왜 하루 10회인가</h3>
 * 팀 생성은 계정당 사실상 평생 한 번 하는 일이다. OCR이 흐릿한 사진을 못 읽어 몇 번 다시 올리는
 * 것까지 감안해도 10회면 넉넉하다. "총 10회"가 아니라 <b>24시간 롤링 윈도우</b>인 이유는,
 * 총량으로 두면 한 번 소진한 계정이 영원히 막혀버리기 때문이다.
 *
 * <h3>한계</h3>
 * <b>인스턴스 메모리에만 산다.</b> 재시작하면 초기화되고, EC2를 여러 대로 늘리면 대당 10회가 된다.
 * 지금은 단일 인스턴스라 문제없지만, 스케일 아웃하면 Redis 같은 공유 저장소로 옮겨야 한다.
 * (업로드는 DB에 아무것도 남기지 않는 설계라 - server-verification-spec.md §6 - DB로 세는 방법이 없다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessRegistrationUploadRateLimiter {

    /** 24시간 안에 허용하는 업로드 횟수. */
    static final int MAX_UPLOADS_PER_WINDOW = 10;

    static final Duration WINDOW = Duration.ofDays(1);

    /**
     * 이 수를 넘으면 만료된 사용자 항목을 쓸어낸다.
     * 없으면 한 번이라도 업로드한 사용자가 계속 쌓여 메모리를 먹는다.
     */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** userId -> 윈도우 안의 업로드 시각들 (최대 MAX_UPLOADS_PER_WINDOW개). */
    private final Map<Long, Deque<Instant>> uploadsByUser = new ConcurrentHashMap<>();

    private final Clock clock;

    /**
     * 업로드 한 번을 기록한다. 한도를 넘으면 기록하지 않고 막는다.
     *
     * @throws GeneralException 24시간 안에 이미 10번 올렸으면 BUSINESS_429_001
     */
    public void checkAndRecord(Long userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);

        sweepIfCrowded(windowStart);

        // 같은 사용자의 동시 업로드가 한도를 함께 뚫지 않도록 사용자별로 잠근다.
        uploadsByUser.compute(userId, (id, uploads) -> {
            Deque<Instant> recent = (uploads == null) ? new ArrayDeque<>() : uploads;

            // 윈도우를 벗어난 오래된 기록부터 버린다 (시각 순으로 쌓이므로 앞에서부터).
            while (!recent.isEmpty() && recent.peekFirst().isBefore(windowStart)) {
                recent.pollFirst();
            }

            if (recent.size() >= MAX_UPLOADS_PER_WINDOW) {
                log.warn("사업자등록증 업로드 한도 초과: userId={}, {}시간 내 {}회",
                        id, WINDOW.toHours(), recent.size());
                throw new GeneralException(BusinessRegistrationErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED);
            }

            recent.addLast(now);
            return recent;
        });
    }

    /** 윈도우가 통째로 지난 사용자는 지운다. 항목이 많아졌을 때만 훑어서 평소 비용을 0으로 둔다. */
    private void sweepIfCrowded(Instant windowStart) {
        if (uploadsByUser.size() < SWEEP_THRESHOLD) {
            return;
        }
        uploadsByUser.values().removeIf(uploads -> {
            Instant last = uploads.peekLast();
            return last == null || last.isBefore(windowStart);
        });
    }
}
