package com.shinhan.klljs.global.local;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.SocialProvider;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserSocialAccount;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.domain.user.repository.UserSocialAccountRepository;
import com.shinhan.klljs.global.security.JwtTokenService;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Local profile에서 홈 대시보드/SQS 연동을 반복 테스트하기 위한 최소 기준 데이터를 만든다.
 * 기본값은 꺼져 있고, LOCAL_DASHBOARD_MOCK_DATA_ENABLED=true일 때만 동작한다.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.local-test-data", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LocalDashboardMockDataInitializer implements ApplicationRunner {

    public static final String BOARD_CODE = "board_gangnam_01";
    public static final String DEVICE_CODE = "adscope-cam-01";

    private static final String SOCIAL_PROVIDER_USER_ID = "local-dashboard-user";
    private static final String TEAM_NAME = "로컬 대시보드 테스트 팀";
    private static final String CAMPAIGN_NAME = "로컬 SQS 실시간 테스트 캠페인";
    private static final LocalDate SAMPLE_EVENT_DATE = LocalDate.of(2026, 7, 7);

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MediaUnitRepository mediaUnitRepository;
    private final CampaignRepository campaignRepository;
    private final TransactionTemplate transactionTemplate;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    @Value("${app.local-test-data.issue-access-token:true}")
    private boolean issueAccessToken;

    @Override
    public void run(ApplicationArguments args) {
        SeedResult seed = transactionTemplate.execute(status -> seed());
        if (seed == null) {
            return;
        }

        log.info(
                "Local dashboard mock data ready: userId={}, teamId={}, mediaUnitId={}, campaignId={}, boardId={}, deviceId={}",
                seed.userId(), seed.teamId(), seed.mediaUnitId(), seed.campaignId(), BOARD_CODE, DEVICE_CODE
        );

        if (issueAccessToken) {
            String accessToken = jwtTokenService.generateAccessToken(seed.userId());
            log.info("Local dashboard test Authorization header: Bearer {}", accessToken);
        }
    }

    private SeedResult seed() {
        User user = findOrCreateUser();
        Team team = findOrCreateTeam();
        ensureTeamMember(team, user);
        MediaUnit mediaUnit = findOrCreateMediaUnit();
        Campaign campaign = findOrCreateCampaign(team, user, mediaUnit);

        return new SeedResult(user.getId(), team.getId(), mediaUnit.getId(), campaign.getId());
    }

    private User findOrCreateUser() {
        return userSocialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, SOCIAL_PROVIDER_USER_ID)
                .map(UserSocialAccount::getUser)
                .orElseGet(() -> {
                    User user = userRepository.save(User.builder()
                            .displayName("로컬 대시보드 테스터")
                            .email("local-dashboard@example.com")
                            .profileImageUrl("https://example.com/local-dashboard-tester.png")
                            .status(UserStatus.ACTIVE)
                            .build());

                    userSocialAccountRepository.save(UserSocialAccount.builder()
                            .user(user)
                            .provider(SocialProvider.KAKAO)
                            .providerUserId(SOCIAL_PROVIDER_USER_ID)
                            .providerEmail(user.getEmail())
                            .connectedAt(LocalDateTime.now(clock))
                            .build());

                    return user;
                });
    }

    private Team findOrCreateTeam() {
        return teamRepository.findAll().stream()
                .filter(team -> TEAM_NAME.equals(team.getTeamName()))
                .findFirst()
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .teamName(TEAM_NAME)
                        .status(TeamStatus.ACTIVE)
                        .build()));
    }

    private void ensureTeamMember(Team team, User user) {
        boolean alreadyActive = teamMemberRepository.existsByUserIdAndTeamIdAndStatus(
                user.getId(), team.getId(), TeamMemberStatus.ACTIVE);
        if (alreadyActive) {
            return;
        }

        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(clock))
                .build());
    }

    private MediaUnit findOrCreateMediaUnit() {
        return mediaUnitRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                        BOARD_CODE, DEVICE_CODE, MediaUnitStatus.ACTIVE
                ).stream()
                .findFirst()
                .orElseGet(() -> mediaUnitRepository.save(MediaUnit.builder()
                        .boardCode(BOARD_CODE)
                        .deviceCode(DEVICE_CODE)
                        .mediaName("강남역 로컬 테스트 매체")
                        .photoUrl("https://example.com/local-media.png")
                        .locationAddress("서울특별시 중구 세종대로 110")
                        .sido("서울특별시")
                        .sigungu("중구")
                        .latitude(new BigDecimal("37.5665000"))
                        .longitude(new BigDecimal("126.9780000"))
                        .widthMm(1200)
                        .heightMm(800)
                        .resolutionWidthPx(1920)
                        .resolutionHeightPx(1080)
                        .shapeTypes(List.of(MediaUnitShapeType.FLAT, MediaUnitShapeType.CORNER))
                        .status(MediaUnitStatus.ACTIVE)
                        .build()));
    }

    private Campaign findOrCreateCampaign(Team team, User createdBy, MediaUnit mediaUnit) {
        return campaignRepository.findByTeamIdIn(List.of(team.getId())).stream()
                .filter(campaign -> CAMPAIGN_NAME.equals(campaign.getCampaignName()))
                .findFirst()
                .orElseGet(() -> {
                    LocalDate today = KstDateTimes.todayKst(LocalDateTime.now(clock));
                    LocalDate executionStartDate = today.minusDays(7).isBefore(SAMPLE_EVENT_DATE)
                            ? today.minusDays(7)
                            : SAMPLE_EVENT_DATE;
                    LocalDate executionEndDate = today.plusDays(7).isAfter(SAMPLE_EVENT_DATE)
                            ? today.plusDays(7)
                            : SAMPLE_EVENT_DATE;
                    return campaignRepository.save(Campaign.builder()
                            .team(team)
                            .mediaUnit(mediaUnit)
                            .createdBy(createdBy)
                            .campaignName(CAMPAIGN_NAME)
                            .brandName("Local Mock Brand")
                            .executionStartDate(executionStartDate)
                            .executionEndDate(executionEndDate)
                            .dailyTargetPlayCount(4320)
                            .description("Local profile에서 실제 AWS SQS polling을 검증하기 위한 캠페인")
                            .imageUrl("https://example.com/local-campaign.png")
                            .creativeType(CampaignCreativeType.IMAGE)
                            .creativeStorageKey("test-fixtures/campaign.png")
                            .creativeOriginalFilename("campaign.png")
                            .status(CampaignStatus.IN_EXECUTION)
                            .build());
                });
    }

    private record SeedResult(Long userId, Long teamId, Long mediaUnitId, Long campaignId) {
    }
}
