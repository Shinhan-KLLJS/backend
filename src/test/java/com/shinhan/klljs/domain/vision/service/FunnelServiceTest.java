package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.campaign.service.DashboardCampaignQueryService;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.vision.dto.FunnelResponse;
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.global.util.KstDateTimes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // 각 테스트 종료 후 자동 롤백
class FunnelServiceTest {

    // KST 2026-07-07 16:43:25 고정 - HourlyGraphServiceTest와 동일한 기준 시각을 재사용한다.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private VisionSummary5sRepository visionSummary5sRepository;
    @Autowired
    private EntityManager entityManager;

    private FunnelService service;
    private Long userId;
    private User createdBy;
    private Team team;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        DashboardCampaignQueryService queryService = new DashboardCampaignQueryService(campaignRepository, teamMemberRepository);
        service = new FunnelService(queryService, visionSummary5sRepository, FIXED_CLOCK);

        team = Team.builder().teamName("팀A").status(TeamStatus.ACTIVE).build();
        entityManager.persist(team);

        createdBy = User.builder().displayName("철수").status(UserStatus.ACTIVE).build();
        entityManager.persist(createdBy);
        userId = createdBy.getId();

        TeamMember member = TeamMember.builder()
                .team(team).user(createdBy).role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(FIXED_CLOCK))
                .build();
        entityManager.persist(member);

        mediaUnit = MediaUnit.builder()
                .boardCode("board-1").deviceCode("device-1").mediaName("매체1")
                .photoUrl("https://example.com/media/1.png")
                .locationAddress("서울시 어딘가")
                .sido("서울특별시").sigungu("강남구")
                .latitude(new java.math.BigDecimal("37.5000000"))
                .longitude(new java.math.BigDecimal("127.0000000"))
                .widthMm(1200).heightMm(800)
                .resolutionWidthPx(1920).resolutionHeightPx(1080)
                .shapeTypes(List.of(MediaUnitShapeType.FLAT))
                .status(MediaUnitStatus.ACTIVE)
                .build();
        entityManager.persist(mediaUnit);
    }

    @Test
    void beforeExecution_returnsNullMetricsButKeepsTrafficAreaMeta() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        FunnelResponse response = service.getFunnel(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.effectivePeriod()).isNull();
        assertThat(response.aggregationCutoffTime()).isNull();
        assertThat(response.aggregationUnit()).isEqualTo("MINUTE");
        assertThat(response.refreshIntervalSec()).isEqualTo(60);

        // trafficArea는 매체 위치 정보라 집행 전이어도 dataDateRange 빼고는 채워진다.
        assertThat(response.trafficArea().gridSizeMeter()).isEqualTo(250);
        assertThat(response.trafficArea().isMock()).isTrue();
        assertThat(response.trafficArea().dataDateRange()).isNull();

        assertThat(response.metrics().totalTrafficCount().value()).isNull();
        assertThat(response.metrics().exposedPopulationCount().value()).isNull();
        assertThat(response.metrics().attentionPopulationCount().value()).isNull();
        assertThat(response.metrics().attentionConversionRate().value()).isNull();
    }

    @Test
    void fullyPastPeriod_sumsWholeRangeAndOmitsYesterdayComparison() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 0), 100, 20);
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 2, 15, 0, 0), 50, 10);

        FunnelResponse response = service.getFunnel(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));

        assertThat(response.aggregationCutoffTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 3, 0, 0, 0, 0, KstDateTimes.KST));

        assertThat(response.metrics().exposedPopulationCount().value()).isEqualTo(150L);
        assertThat(response.metrics().attentionPopulationCount().value()).isEqualTo(30L);
        // attentionConversionRate = 30/150*100 = 20.0
        assertThat(response.metrics().attentionConversionRate().value()).isEqualTo(20.0);

        // 오늘 조회가 아니므로(과거 기간) 어제 대비 증가율은 전부 null.
        assertThat(response.metrics().exposedPopulationCount().yesterdayComparison()).isNull();
        assertThat(response.metrics().attentionConversionRate().yesterdayComparison()).isNull();
        assertThat(response.metrics().totalTrafficCount().yesterdayComparison()).isNull();

        // mock 전체 유동인구 = 하루 50,000 * 2일
        assertThat(response.metrics().totalTrafficCount().value()).isEqualTo(100_000L);
    }

    @Test
    void todayQuery_includesYesterdayComparisonAndExcludesInProgressMinute() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 오늘(07-07) 완료된 데이터.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 7, 9, 0, 0), 200, 40);
        // 오늘, 지금(16:43:25) 기준 아직 진행 중인 분(16:43:00~16:43:25) -> 제외돼야 한다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 7, 16, 43, 10), 9999, 9999);
        // 어제(07-06) 같은 범위(00:00~16:43) 안의 데이터.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 6, 9, 0, 0), 100, 10);

        FunnelResponse response = service.getFunnel(
                userId, campaign.getId(), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7));

        // cutoff = 지금 진행 중인 분(16:43)의 시작 시각 -> 16:43:10 row는 제외된다.
        assertThat(response.aggregationCutoffTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 7, 16, 43, 0, 0, KstDateTimes.KST));
        assertThat(response.metrics().exposedPopulationCount().value()).isEqualTo(200L);
        assertThat(response.metrics().attentionPopulationCount().value()).isEqualTo(40L);

        FunnelResponse.PopulationMetric.YesterdayComparison exposedComparison =
                response.metrics().exposedPopulationCount().yesterdayComparison();
        assertThat(exposedComparison).isNotNull();
        assertThat(exposedComparison.baseDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(exposedComparison.baseValue()).isEqualTo(100L);
        // (200-100)/100*100 = 100.0
        assertThat(exposedComparison.increaseRate()).isEqualTo(100.0);

        // 주목 전환률: 오늘 40/200*100=20.0, 어제 10/100*100=10.0 -> 증가율 (20-10)/10*100=100.0
        FunnelResponse.RateMetric conversion = response.metrics().attentionConversionRate();
        assertThat(conversion.value()).isEqualTo(20.0);
        assertThat(conversion.yesterdayComparison().baseValue()).isEqualTo(10.0);
        assertThat(conversion.yesterdayComparison().increaseRate()).isEqualTo(100.0);

        // mock 전체 유동인구도 오늘 조회면 어제 비교값이 항상 같이 내려온다 (고정 상수라 증가율은 0.0).
        assertThat(response.metrics().totalTrafficCount().yesterdayComparison()).isNotNull();
        assertThat(response.metrics().totalTrafficCount().yesterdayComparison().increaseRate()).isEqualTo(0.0);
    }

    @Test
    void zeroExposedPopulation_conversionRateIsNullInsteadOfDivideByZero() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        // ots_count=0인 row만 있는 상황(주목만 있고 노출은 0인 건 현실적으로 이상하지만,
        // 분모가 0일 때 죽지 않는지 확인하기 위한 경계값 테스트).

        FunnelResponse response = service.getFunnel(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.metrics().exposedPopulationCount().value()).isEqualTo(0L);
        assertThat(response.metrics().attentionConversionRate().value()).isNull();
    }

    /** eventTimeKst: 테스트 가독성을 위해 KST 기준 시각으로 받아서 내부적으로 UTC로 변환해 저장한다. */
    private VisionSummary5s createVisionRow(Campaign campaign, LocalDateTime eventTimeKst, int otsCount, int ltsCount) {
        LocalDateTime eventTimeUtc = KstDateTimes.toUtc(eventTimeKst);
        VisionSummary5s row = VisionSummary5s.builder()
                .mediaUnit(mediaUnit)
                .campaign(campaign)
                .deviceId("device-1")
                .boardId("board-1")
                .seq(1L)
                .eventTime(eventTimeUtc)
                .intervalSec(BigDecimal.valueOf(5))
                .receivedAt(eventTimeUtc)
                .rawPayload(null)
                .otsCount(otsCount)
                .ltsCount(ltsCount)
                .otsMaleCount(0).otsFemaleCount(0).ltsMaleCount(0).ltsFemaleCount(0)
                .otsMaleUnder10(0).otsMale10s(0).otsMale20s(0).otsMale30s(0).otsMale40s(0).otsMale50s(0).otsMale60plus(0)
                .otsFemaleUnder10(0).otsFemale10s(0).otsFemale20s(0).otsFemale30s(0).otsFemale40s(0).otsFemale50s(0).otsFemale60plus(0)
                .ltsMaleUnder10(0).ltsMale10s(0).ltsMale20s(0).ltsMale30s(0).ltsMale40s(0).ltsMale50s(0).ltsMale60plus(0)
                .ltsFemaleUnder10(0).ltsFemale10s(0).ltsFemale20s(0).ltsFemale30s(0).ltsFemale40s(0).ltsFemale50s(0).ltsFemale60plus(0)
                .avgDwellSec(BigDecimal.ZERO)
                .dwellSumSec(BigDecimal.ZERO)
                .dwell1ToUnder2s(0).dwell2ToUnder3s(0).dwell3ToUnder4s(0).dwell4sAndOver(0)
                .build();
        return visionSummary5sRepository.save(row);
    }

    private Campaign createCampaign(LocalDate executionStartDate, LocalDate executionEndDate) {
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(createdBy)
                .campaignName("캠페인A")
                .brandName("브랜드A")
                .executionStartDate(executionStartDate)
                .executionEndDate(executionEndDate)
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("test-fixtures/campaign.png")
                .creativeOriginalFilename("campaign.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build();
        return campaignRepository.save(campaign);
    }
}
