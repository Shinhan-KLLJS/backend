package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.media.service.MediaUnitCommandService;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 실제 두 스레드가 같은 캠페인을 동시에 삭제 요청했을 때, findByIdForUpdate 잠금이
 * 뒤 트랜잭션을 앞 트랜잭션의 커밋 이후로 미뤄서 정상적으로 CAMPAIGN_NOT_FOUND(404)를
 * 받게 하는지 검증한다 (잠금이 없으면 Hibernate가 stale-state 예외를 던져 500이 된다).
 * H2는 MySQL과 잠금 동작이 완전히 같다는 보장이 없어서 반드시 실제 MySQL로 검증한다.
 * 기본 './gradlew test'에서는 제외되고, MySQL 컨테이너가 준비된 상태에서
 * './gradlew mysqlConcurrencyTest'로만 실행된다.
 */
@Tag("mysql-concurrency")
@SpringBootTest
class TeamCampaignDeleteConcurrencyMySqlTest {

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
    private TeamCampaignCommandService teamCampaignCommandService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private MediaUnitRepository mediaUnitRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void deleteCampaign_underConcurrentRequests_onlyOneSucceedsAndLoserGetsNotFound() throws Exception {
        Team team = teamRepository.save(Team.builder().teamName("동시성 삭제 팀").status(TeamStatus.ACTIVE).build());
        User owner = userRepository.save(User.builder().displayName("소유자").status(UserStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(owner)
                .role(TeamMemberRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(java.time.LocalDateTime.now())
                .build());

        MediaUnit mediaUnit = mediaUnitRepository.save(MediaUnit.builder()
                .boardCode(MediaUnitCommandService.MVP_BOARD_CODE)
                .deviceCode(MediaUnitCommandService.MVP_DEVICE_CODE)
                .mediaName("동시성 삭제 매체")
                .photoUrl("https://cdn.example.com/media.jpg")
                .locationAddress("서울 강남구")
                .sido("서울특별시")
                .sigungu("강남구")
                .latitude(new BigDecimal("37.5000000"))
                .longitude(new BigDecimal("127.0000000"))
                .widthMm(1000)
                .heightMm(500)
                .resolutionWidthPx(1920)
                .resolutionHeightPx(1080)
                .shapeTypes(List.of(MediaUnitShapeType.FLAT))
                .status(MediaUnitStatus.ACTIVE)
                .build());

        LocalDate today = LocalDate.now();
        Campaign campaign = campaignRepository.save(Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(owner)
                .campaignName("동시성 삭제 대상")
                .brandName("브랜드")
                .executionStartDate(today.minusDays(1))
                .executionEndDate(today.plusDays(1))
                .dailyTargetPlayCount(200)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/concurrency-delete")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Callable<Void> attemptDelete = () -> {
            startLatch.await();
            teamCampaignCommandService.deleteCampaign(owner.getId(), team.getId(), campaign.getId());
            return null;
        };

        Future<Void> first = executor.submit(attemptDelete);
        Future<Void> second = executor.submit(attemptDelete);
        startLatch.countDown();

        int successCount = 0;
        int notFoundCount = 0;
        int unexpectedFailureCount = 0;
        for (Future<Void> future : List.of(first, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof GeneralException generalException
                        && generalException.getErrorCode() == CampaignErrorCode.CAMPAIGN_NOT_FOUND) {
                    notFoundCount++;
                } else {
                    unexpectedFailureCount++;
                }
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(notFoundCount).isEqualTo(1);
        assertThat(unexpectedFailureCount)
                .as("잠금 없이 두 트랜잭션이 같은 행을 동시에 읽으면 진 쪽이 stale-state 예외(500)로 실패한다")
                .isZero();
        assertThat(campaignRepository.findById(campaign.getId())).isEmpty();
    }
}
