package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
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
import com.shinhan.klljs.domain.vision.dto.HourlyGraphResponse;
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
class HourlyGraphServiceTest {

    // KST 2026-07-07 16:43:25 고정 - "오늘"이 07-07이고, 16시대가 아직 43분 25초밖에 안 지난
    // "진행 중인 시간대"라는 스펙 예시(0절, 5-2절)와 그대로 맞춰서 테스트하기 위한 고정 시각.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private CampaignCreativeProperties campaignCreativeProperties;
    @Autowired
    private VisionSummary5sRepository visionSummary5sRepository;
    @Autowired
    private EntityManager entityManager;

    private HourlyGraphService service;
    private Long userId;
    private User createdBy;
    private Team team;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        DashboardCampaignQueryService queryService = new DashboardCampaignQueryService(campaignRepository, teamMemberRepository, campaignCreativeProperties);
        service = new HourlyGraphService(queryService, visionSummary5sRepository, FIXED_CLOCK);

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
    void beforeExecution_returnsNullEffectivePeriodAndEmptyPoints() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        HourlyGraphResponse response = service.getHourlyGraph(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.effectivePeriod()).isNull();
        assertThat(response.aggregationCutoffTime()).isNull();
        assertThat(response.points()).isEmpty();
        assertThat(response.aggregationUnit()).isEqualTo("HOUR");
        assertThat(response.refreshIntervalSec()).isEqualTo(3600);
    }

    @Test
    void fullyPastPeriod_aggregatesWholeRangeAndCutoffIsDayAfterEnd() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 같은 시간(KST 07-01 15시) 버킷에 두 row -> 합산되어야 한다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 15, 0, 0), 10, 2);
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 15, 0, 5), 20, 3);
        // 다른 시각(16시) -> 별도 포인트.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 16, 0, 0), 5, 1);
        // 다른 날짜(07-03)지만 시각(9시)이 앞의 두 시각(15,16)과 겹치지 않음 -> 별도 포인트.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 3, 9, 0, 0), 7, 1);

        // 선택 기간(07-01~07-03) 전체가 "오늘"(07-07)보다 과거 -> 완전히 확정된 데이터로 처리돼야 한다.
        HourlyGraphResponse response = service.getHourlyGraph(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        assertThat(response.effectivePeriod().startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.effectivePeriod().endDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        // cutoff = 조회 상한(선택 기간 마지막 날 다음날 00:00 KST) 그 자체.
        assertThat(response.aggregationCutoffTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 4, 0, 0, 0, 0, KstDateTimes.KST));

        // 날짜는 버리고 시각(0~23시) 오름차순으로 정렬되므로 9시(07-03 데이터) -> 15시 -> 16시 순.
        // eventTime의 날짜 부분은 실제 날짜가 아니라 effectivePeriod 시작일(07-01)로 고정된 앵커다.
        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(0).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 9, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(0).exposedPopulationCount()).isEqualTo(7);
        assertThat(response.points().get(0).attentionPopulationCount()).isEqualTo(1);

        assertThat(response.points().get(1).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 15, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(1).exposedPopulationCount()).isEqualTo(30);
        assertThat(response.points().get(1).attentionPopulationCount()).isEqualTo(5);

        assertThat(response.points().get(2).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 16, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(2).exposedPopulationCount()).isEqualTo(5);
    }

    @Test
    void multiDayPeriod_sumsSameHourOfDayAcrossDifferentDatesIntoOnePoint() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31));

        // 세 날짜(07-01~07-03, "오늘"인 07-07보다 과거) 모두 같은 시각(06시)에 데이터
        // -> 날짜별로 나뉘지 않고 한 포인트로 합산돼야 한다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 6, 0, 0), 10, 1);
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 2, 6, 0, 0), 20, 2);
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 3, 6, 0, 0), 30, 3);
        // 07-02만 7시 데이터도 있음 -> 06시와 별도 포인트.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 2, 7, 0, 0), 5, 0);

        HourlyGraphResponse response = service.getHourlyGraph(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        assertThat(response.points()).hasSize(2);

        // 06시: 세 날짜 합산 = 10+20+30, 1+2+3. eventTime 날짜는 effectivePeriod 시작일(07-01)로 고정.
        assertThat(response.points().get(0).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 6, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(0).exposedPopulationCount()).isEqualTo(60);
        assertThat(response.points().get(0).attentionPopulationCount()).isEqualTo(6);

        // 07시: 07-02 데이터 하나뿐.
        assertThat(response.points().get(1).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 7, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(1).exposedPopulationCount()).isEqualTo(5);
        assertThat(response.points().get(1).attentionPopulationCount()).isEqualTo(0);
    }

    @Test
    void todayIncluded_excludesStillInProgressHour() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 완료된 시간대(15시) -> 포함돼야 한다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 7, 15, 30, 0), 8, 2);
        // 지금(16:43:25) 기준 아직 진행 중인 시간대(16시) -> 제외돼야 한다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 7, 16, 10, 0), 100, 50);

        HourlyGraphResponse response = service.getHourlyGraph(
                userId, campaign.getId(), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7));

        // cutoff = 지금 진행 중인 시간(16시)의 시작 시각.
        assertThat(response.aggregationCutoffTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 7, 16, 0, 0, 0, KstDateTimes.KST));

        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).eventTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 7, 15, 0, 0, 0, KstDateTimes.KST));
        assertThat(response.points().get(0).exposedPopulationCount()).isEqualTo(8);
        assertThat(response.points().get(0).attentionPopulationCount()).isEqualTo(2);
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
