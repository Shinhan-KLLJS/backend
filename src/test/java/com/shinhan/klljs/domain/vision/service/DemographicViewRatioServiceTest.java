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
import com.shinhan.klljs.domain.vision.dto.AgeGroup;
import com.shinhan.klljs.domain.vision.dto.DemographicViewRatioResponse;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class DemographicViewRatioServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private VisionSummary5sRepository visionSummary5sRepository;
    @Autowired
    private EntityManager entityManager;

    private DemographicViewRatioService service;
    private Long userId;
    private User createdBy;
    private Team team;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        DashboardCampaignQueryService queryService = new DashboardCampaignQueryService(campaignRepository, teamMemberRepository);
        service = new DemographicViewRatioService(queryService, visionSummary5sRepository, FIXED_CLOCK);

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
    void beforeExecution_returnsNullGenderSummaryAndEmptyAgeGroups() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        DemographicViewRatioResponse response = service.getDemographicViewRatio(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.genderSummary().maleRatio()).isNull();
        assertThat(response.genderSummary().femaleRatio()).isNull();
        assertThat(response.ageGroups()).isEmpty();
    }

    @Test
    void computesRatiosUsingAgeColumnSumAsDenominator() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 남성 합=50(2+3+20+15+5+3+2), 여성 합=35(1+2+15+10+4+2+1) -> 전체 합=85
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 0),
                2, 3, 20, 15, 5, 3, 2,
                1, 2, 15, 10, 4, 2, 1);

        DemographicViewRatioResponse response = service.getDemographicViewRatio(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.genderSummary().maleRatio()).isCloseTo(58.8235, within(0.001));
        assertThat(response.genderSummary().femaleRatio()).isCloseTo(41.1764, within(0.001));

        Map<AgeGroup, DemographicViewRatioResponse.AgeGroupRatio> byAgeGroup = response.ageGroups().stream()
                .collect(Collectors.toMap(DemographicViewRatioResponse.AgeGroupRatio::ageGroup, a -> a));
        assertThat(byAgeGroup).hasSize(7);

        DemographicViewRatioResponse.AgeGroupRatio age20s = byAgeGroup.get(AgeGroup.AGE_20S);
        // maleRatio = 20/85*100, femaleRatio = 15/85*100
        assertThat(age20s.maleRatio()).isCloseTo(23.5294, within(0.001));
        assertThat(age20s.femaleRatio()).isCloseTo(17.6470, within(0.001));
        assertThat(age20s.totalRatio()).isCloseTo(age20s.maleRatio() + age20s.femaleRatio(), within(0.0001));
        // maleShareRatio = 20/50*100 = 40.0, femaleShareRatio = 15/35*100 = 42.857...
        assertThat(age20s.maleShareRatio()).isCloseTo(40.0, within(0.001));
        assertThat(age20s.femaleShareRatio()).isCloseTo(42.8571, within(0.001));

        // 불변식: 7개 연령대의 totalRatio 합 = 100%, maleShareRatio 합 = 100%, femaleShareRatio 합 = 100%
        double totalRatioSum = response.ageGroups().stream().mapToDouble(DemographicViewRatioResponse.AgeGroupRatio::totalRatio).sum();
        double maleShareSum = response.ageGroups().stream().mapToDouble(DemographicViewRatioResponse.AgeGroupRatio::maleShareRatio).sum();
        double femaleShareSum = response.ageGroups().stream().mapToDouble(DemographicViewRatioResponse.AgeGroupRatio::femaleShareRatio).sum();
        assertThat(totalRatioSum).isCloseTo(100.0, within(0.01));
        assertThat(maleShareSum).isCloseTo(100.0, within(0.01));
        assertThat(femaleShareSum).isCloseTo(100.0, within(0.01));
    }

    @Test
    void zeroData_genderSummaryIsNullButAgeGroupRatiosAreZero() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        // 데이터가 아예 없는 경계값 테스트 (분모 0으로 나누지 않는지 확인).

        DemographicViewRatioResponse response = service.getDemographicViewRatio(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.genderSummary().maleRatio()).isNull();
        assertThat(response.genderSummary().femaleRatio()).isNull();
        assertThat(response.ageGroups()).hasSize(7);
        assertThat(response.ageGroups()).allSatisfy(a -> {
            assertThat(a.maleRatio()).isEqualTo(0.0);
            assertThat(a.femaleRatio()).isEqualTo(0.0);
            assertThat(a.totalRatio()).isEqualTo(0.0);
            assertThat(a.maleShareRatio()).isEqualTo(0.0);
            assertThat(a.femaleShareRatio()).isEqualTo(0.0);
        });
    }

    private VisionSummary5s createVisionRow(
            Campaign campaign, LocalDateTime eventTimeKst,
            int ltsMaleUnder10, int ltsMale10s, int ltsMale20s, int ltsMale30s, int ltsMale40s, int ltsMale50s, int ltsMale60plus,
            int ltsFemaleUnder10, int ltsFemale10s, int ltsFemale20s, int ltsFemale30s, int ltsFemale40s, int ltsFemale50s, int ltsFemale60plus
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
                .ltsCount(0)
                .otsMaleCount(0).otsFemaleCount(0).ltsMaleCount(0).ltsFemaleCount(0)
                .otsMaleUnder10(0).otsMale10s(0).otsMale20s(0).otsMale30s(0).otsMale40s(0).otsMale50s(0).otsMale60plus(0)
                .otsFemaleUnder10(0).otsFemale10s(0).otsFemale20s(0).otsFemale30s(0).otsFemale40s(0).otsFemale50s(0).otsFemale60plus(0)
                .ltsMaleUnder10(ltsMaleUnder10).ltsMale10s(ltsMale10s).ltsMale20s(ltsMale20s).ltsMale30s(ltsMale30s)
                .ltsMale40s(ltsMale40s).ltsMale50s(ltsMale50s).ltsMale60plus(ltsMale60plus)
                .ltsFemaleUnder10(ltsFemaleUnder10).ltsFemale10s(ltsFemale10s).ltsFemale20s(ltsFemale20s).ltsFemale30s(ltsFemale30s)
                .ltsFemale40s(ltsFemale40s).ltsFemale50s(ltsFemale50s).ltsFemale60plus(ltsFemale60plus)
                .avgDwellSec(BigDecimal.ZERO)
                .dwellSumSec(BigDecimal.ZERO)
                .dwell1ToUnder2s(0).dwell2ToUnder3s(0).dwell3ToUnder4s(0).dwell4sAndOver(0)
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
