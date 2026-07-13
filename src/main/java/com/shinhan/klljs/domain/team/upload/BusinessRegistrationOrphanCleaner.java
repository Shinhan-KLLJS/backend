package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 참조되지 않는 사업자등록증 파일을 S3에서 지운다 (team-creation-api-spec.md 9절).
 *
 * <h3>왜 필요한가</h3>
 * 업로드는 성공했는데 팀 생성까지 가지 않고 이탈하면, S3에는 파일이 남고 DB에는 아무 기록도 없다.
 * 업로드 시점에는 DB를 건드리지 않기로 했기 때문이다(server-verification-spec.md §6).
 * 이 파일들은 아무도 참조하지 않은 채 계속 쌓이는데, 대표자명·주소·사업자등록번호가 찍힌
 * <b>민감 문서</b>라 방치할 수 없다.
 *
 * <h3>어떻게 찾나</h3>
 * 고아 파일의 키는 DB 어디에도 없다. 그래서 S3 목록을 페이지 단위로 훑고, 각 페이지의 키를
 * {@code team_business_registrations.document_storage_key}와 대조해 참조가 없는 것만 지운다.
 *
 * <h3>왜 2일인가</h3>
 * 업로드 토큰의 수명은 1시간이다. 보관 기간을 토큰 수명보다 훨씬 길게 잡아야, 팀 생성을 진행 중인
 * 사용자의 파일이 배치에 지워지는 일이 없다. 그래야 "서명이 유효하다 = 그 객체가 아직 있다"는
 * 전제가 성립하고, 팀 생성 시점에 S3를 다시 조회하지 않아도 된다(문서 5절).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.upload.business-registration.cleanup", name = "enabled", havingValue = "true")
public class BusinessRegistrationOrphanCleaner {

    private final BusinessRegistrationDocumentStorage storage;
    private final TeamBusinessRegistrationRepository registrationRepository;
    private final Clock clock;
    private final int retentionDays;

    public BusinessRegistrationOrphanCleaner(
            BusinessRegistrationDocumentStorage storage,
            TeamBusinessRegistrationRepository registrationRepository,
            Clock clock,
            @Value("${app.upload.business-registration.cleanup.retention-days}") int retentionDays) {

        this.storage = storage;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /**
     * 사용량이 적은 새벽에 하루 한 번 돈다. 시간대를 KST로 못박는다 - 서버 타임존이 UTC라
     * 지정하지 않으면 한국 시각 낮에 도는 셈이 된다.
     */
    @Scheduled(cron = "${app.upload.business-registration.cleanup.cron}", zone = "Asia/Seoul")
    public void cleanUpOrphans() {
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        AtomicInteger deleted = new AtomicInteger();

        try {
            storage.forEachKeyOlderThan(cutoff, page -> deleted.addAndGet(deleteOrphansIn(page)));
        } catch (RuntimeException e) {
            // 배치가 죽어도 서비스는 계속 떠 있어야 한다. 다음 회차가 같은 대상을 다시 잡는다.
            log.error("사업자등록증 orphan 정리 중 오류. 다음 회차에 다시 시도한다.", e);
            return;
        }

        log.info("사업자등록증 orphan 정리 완료: {}건 삭제 (기준 {}일, cutoff={})",
                deleted.get(), retentionDays, cutoff);
    }

    /**
     * 한 페이지 분량의 키에서 DB 참조가 없는 것만 골라 지운다.
     *
     * 참조 조회는 페이지당 딱 한 번이다 - 키 하나씩 물으면 페이지(최대 1000개)마다 1000번의
     * 쿼리가 나간다.
     */
    private int deleteOrphansIn(List<String> keysInPage) {
        Set<String> referenced = registrationRepository.findReferencedKeys(keysInPage);

        List<String> orphans = keysInPage.stream()
                .filter(key -> !referenced.contains(key))
                .toList();

        if (orphans.isEmpty()) {
            return 0;
        }

        storage.deleteAll(orphans);
        return orphans.size();
    }
}
