package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
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
import com.shinhan.klljs.domain.vision.dto.AverageWatchTimeResponse;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class AverageWatchTimeServiceTest {

    // KST 2026-07-07 16:43:25 고정 - 다른 vision 서비스 테스트와 동일한 기준 시각을 재사용한다.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private VisionSummary5sRepository visionSummary5sRepository;
    @Autowired
    private EntityManager entityManager;

    private AverageWatchTimeService service;
    private Long userId;
    private User createdBy;
    private Team team;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        DashboardCampaignQueryService queryService = new DashboardCampaignQueryService(campaignRepository, teamMemberRepository);
        service = new AverageWatchTimeService(queryService, visionSummary5sRepository, FIXED_CLOCK);

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
                .widthMm(1200).heightMm(800)
                .resolutionWidthPx(1920).resolutionHeightPx(1080)
                .shapeTypes(List.of(MediaUnitShapeType.FLAT))
                .status(MediaUnitStatus.ACTIVE)
                .build();
        entityManager.persist(mediaUnit);
    }

    @Test
    void beforeExecution_returnsNullAverageAndEmptyBuckets() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        AverageWatchTimeResponse response = service.getAverageWatchTime(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.effectivePeriod()).isNull();
        assertThat(response.aggregationCutoffTime()).isNull();
        assertThat(response.averageWatchTimeSec()).isNull();
        assertThat(response.watchTimeBuckets()).isEmpty();
        assertThat(response.aggregationUnit()).isEqualTo("MINUTE");
        assertThat(response.refreshIntervalSec()).isEqualTo(60);
    }

    @Test
    void usesWeightedAverageNotSimpleAverageOfWindowAverages() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // window 1: ltsCount=10, dwellSumSec=20 (창 하나만 보면 평균 2.0초)
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 0), 10, new BigDecimal("20.000"), 5, 3, 1, 1);
        // window 2: ltsCount=30, dwellSumSec=90 (창 하나만 보면 평균 3.0초)
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 5), 30, new BigDecimal("90.000"), 15, 10, 3, 2);

        AverageWatchTimeResponse response = service.getAverageWatchTime(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        // 가중평균 = (20+90)/(10+30) = 2.75. 단순평균(2.0+3.0)/2=2.5와는 달라야 한다.
        assertThat(response.averageWatchTimeSec()).isCloseTo(2.75, within(0.0001));

        Map<String, AverageWatchTimeResponse.WatchTimeBucket> buckets = response.watchTimeBuckets().stream()
                .collect(java.util.stream.Collectors.toMap(AverageWatchTimeResponse.WatchTimeBucket::bucket, b -> b));

        // dwell 합계: 1-2초=20, 2-3초=13, 3-4초=4, 4초이상=3 (총 40)
        assertThat(buckets.get("1_TO_2S").count()).isEqualTo(20);
        assertThat(buckets.get("1_TO_2S").ratio()).isCloseTo(50.0, within(0.01));
        assertThat(buckets.get("2_TO_3S").count()).isEqualTo(13);
        assertThat(buckets.get("2_TO_3S").ratio()).isCloseTo(32.5, within(0.01));
        assertThat(buckets.get("3_TO_4S").count()).isEqualTo(4);
        assertThat(buckets.get("3_TO_4S").ratio()).isCloseTo(10.0, within(0.01));
        assertThat(buckets.get("OVER_4S").count()).isEqualTo(3);
        assertThat(buckets.get("OVER_4S").ratio()).isCloseTo(7.5, within(0.01));
    }

    @Test
    void zeroLtsCount_averageIsNullButBucketsAreZero() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        // lts_count=0인 row만 있는 경계값 테스트 (분모 0으로 나누지 않는지 확인).
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 0), 0, BigDecimal.ZERO, 0, 0, 0, 0);

        AverageWatchTimeResponse response = service.getAverageWatchTime(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.averageWatchTimeSec()).isNull();
        assertThat(response.watchTimeBuckets()).allSatisfy(b -> assertThat(b.ratio()).isEqualTo(0.0));
    }

    private VisionSummary5s createVisionRow(
            Campaign campaign, LocalDateTime eventTimeKst, int ltsCount, BigDecimal dwellSumSec,
            int dwell1To2s, int dwell2To3s, int dwell3To4s, int dwellOver4s
    ) {
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
                .otsCount(0)
                .ltsCount(ltsCount)
                .otsMaleCount(0).otsFemaleCount(0).ltsMaleCount(0).ltsFemaleCount(0)
                .otsMaleUnder10(0).otsMale10s(0).otsMale20s(0).otsMale30s(0).otsMale40s(0).otsMale50s(0).otsMale60plus(0)
                .otsFemaleUnder10(0).otsFemale10s(0).otsFemale20s(0).otsFemale30s(0).otsFemale40s(0).otsFemale50s(0).otsFemale60plus(0)
                .ltsMaleUnder10(0).ltsMale10s(0).ltsMale20s(0).ltsMale30s(0).ltsMale40s(0).ltsMale50s(0).ltsMale60plus(0)
                .ltsFemaleUnder10(0).ltsFemale10s(0).ltsFemale20s(0).ltsFemale30s(0).ltsFemale40s(0).ltsFemale50s(0).ltsFemale60plus(0)
                .avgDwellSec(BigDecimal.ZERO)
                .dwellSumSec(dwellSumSec)
                .dwell1ToUnder2s(dwell1To2s).dwell2ToUnder3s(dwell2To3s).dwell3ToUnder4s(dwell3To4s).dwell4sAndOver(dwellOver4s)
                .build();
        return visionSummary5sRepository.save(row);
    }

    private Campaign createCampaign(LocalDate executionStartDate, LocalDate executionEndDate) {
        Campaign campaign = Campaign.builder()
                .team(team)
                .createdBy(createdBy)
                .campaignName("캠페인A")
                .brandName("브랜드A")
                .executionStartDate(executionStartDate)
                .executionEndDate(executionEndDate)
                .dailyTargetPlayCount(100)
                .status(CampaignStatus.IN_EXECUTION)
                .build();
        return campaignRepository.save(campaign);
    }
}
