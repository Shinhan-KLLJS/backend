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
import com.shinhan.klljs.domain.vision.dto.AgeGroup;
import com.shinhan.klljs.domain.vision.dto.HourlyAgeExposureResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class HourlyAgeExposureServiceTest {

    // KST 2026-07-07 16:43:25 고정 - 현재 KST 시(hour)는 16.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T07:43:25Z"), ZoneOffset.UTC);

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private VisionSummary5sRepository visionSummary5sRepository;
    @Autowired
    private EntityManager entityManager;

    private HourlyAgeExposureService service;
    private Long userId;
    private User createdBy;
    private Team team;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        DashboardCampaignQueryService queryService = new DashboardCampaignQueryService(campaignRepository, teamMemberRepository);
        service = new HourlyAgeExposureService(queryService, visionSummary5sRepository, FIXED_CLOCK);

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
    void beforeExecution_returnsEmptyHoursAndCellsButFixedAgeGroups() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        HourlyAgeExposureResponse response = service.getHourlyAgeExposure(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.hours()).isEmpty();
        assertThat(response.cells()).isEmpty();
        assertThat(response.ageGroups()).hasSize(7);
    }

    @Test
    void relabelsUtcHourToKstHourOfDayCorrectly() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // KST 07-01 14:00 = UTC 07-01 05:00. UTC 그대로 라벨링하면 "05"로 잘못 나온다.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 14, 0, 0), AgeGroup.AGE_20S, 10, 5);

        // 완전히 과거 날짜 조회 (07-01 하루).
        HourlyAgeExposureResponse response = service.getHourlyAgeExposure(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.cells()).hasSize(1);
        HourlyAgeExposureResponse.Cell cell = response.cells().get(0);
        assertThat(cell.hour()).isEqualTo("14");
        assertThat(cell.ageGroup()).isEqualTo(AgeGroup.AGE_20S);
        assertThat(cell.exposureCount()).isEqualTo(15);
        assertThat(cell.maleExposureCount()).isEqualTo(10);
        assertThat(cell.femaleExposureCount()).isEqualTo(5);
    }

    @Test
    void intensityLevelsAreNormalizedIndependentlyPerView() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 셀1(10시, 20대): 남성이 압도적으로 많음(전체 110, 남 100, 여 10)
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 10, 0, 0), AgeGroup.AGE_20S, 100, 10);
        // 셀2(11시, 30대): 여성이 압도적으로 많음(전체 110, 남 10, 여 100) - 전체 노출은 셀1과 동일.
        createVisionRow(campaign, LocalDateTime.of(2026, 7, 1, 11, 0, 0), AgeGroup.AGE_30S, 10, 100);

        HourlyAgeExposureResponse response = service.getHourlyAgeExposure(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.cells()).hasSize(2);
        HourlyAgeExposureResponse.Cell cell1 = findCell(response, "10", AgeGroup.AGE_20S);
        HourlyAgeExposureResponse.Cell cell2 = findCell(response, "11", AgeGroup.AGE_30S);

        // 전체 탭 기준: 두 셀 다 노출 110으로 최댓값과 같으므로 레벨 4.
        assertThat(cell1.intensityLevel()).isEqualTo(4);
        assertThat(cell2.intensityLevel()).isEqualTo(4);

        // 남성 탭 기준: 셀1(남100)이 최댓값이라 레벨4, 셀2(남10)는 10/100*4=0.4 -> ceil=1.
        assertThat(cell1.maleIntensityLevel()).isEqualTo(4);
        assertThat(cell2.maleIntensityLevel()).isEqualTo(1);

        // 여성 탭 기준: 셀2(여100)가 최댓값이라 레벨4, 셀1(여10)은 레벨1 - 남성 탭과 반대 패턴.
        assertThat(cell1.femaleIntensityLevel()).isEqualTo(1);
        assertThat(cell2.femaleIntensityLevel()).isEqualTo(4);
    }

    @Test
    void hoursAxis_todayQuery_endsAtCurrentKstHour() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        HourlyAgeExposureResponse response = service.getHourlyAgeExposure(
                userId, campaign.getId(), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7));

        // 운영 시작(06시)부터 지금 KST 시(16시)까지.
        assertThat(response.hours()).containsExactly(
                "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16");
    }

    @Test
    void hoursAxis_fullyPastQuery_spansWholeOperatingDay() {
        Campaign campaign = createCampaign(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        HourlyAgeExposureResponse response = service.getHourlyAgeExposure(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(response.hours()).hasSize(18); // 06~23시
        assertThat(response.hours().get(0)).isEqualTo("06");
        assertThat(response.hours().get(response.hours().size() - 1)).isEqualTo("23");
    }

    private HourlyAgeExposureResponse.Cell findCell(HourlyAgeExposureResponse response, String hour, AgeGroup ageGroup) {
        Optional<HourlyAgeExposureResponse.Cell> found = response.cells().stream()
                .filter(c -> c.hour().equals(hour) && c.ageGroup() == ageGroup)
                .findFirst();
        assertThat(found).as("cell for hour=%s ageGroup=%s", hour, ageGroup).isPresent();
        return found.get();
    }

    private VisionSummary5s createVisionRow(
            Campaign campaign, LocalDateTime eventTimeKst, AgeGroup ageGroup, int maleCount, int femaleCount
    ) {
        LocalDateTime eventTimeUtc = KstDateTimes.toUtc(eventTimeKst);
        VisionSummary5s.VisionSummary5sBuilder builder = VisionSummary5s.builder()
                .mediaUnit(mediaUnit)
                .campaign(campaign)
                .deviceId("device-1")
                .boardId("board-1")
                .seq(1L)
                .eventTime(eventTimeUtc)
                .intervalSec(BigDecimal.valueOf(5))
                .receivedAt(eventTimeUtc)
                .rawPayload(null)
                .otsCount(maleCount + femaleCount)
                .ltsCount(0)
                .otsMaleCount(maleCount).otsFemaleCount(femaleCount).ltsMaleCount(0).ltsFemaleCount(0)
                .otsMaleUnder10(0).otsMale10s(0).otsMale20s(0).otsMale30s(0).otsMale40s(0).otsMale50s(0).otsMale60plus(0)
                .otsFemaleUnder10(0).otsFemale10s(0).otsFemale20s(0).otsFemale30s(0).otsFemale40s(0).otsFemale50s(0).otsFemale60plus(0)
                .ltsMaleUnder10(0).ltsMale10s(0).ltsMale20s(0).ltsMale30s(0).ltsMale40s(0).ltsMale50s(0).ltsMale60plus(0)
                .ltsFemaleUnder10(0).ltsFemale10s(0).ltsFemale20s(0).ltsFemale30s(0).ltsFemale40s(0).ltsFemale50s(0).ltsFemale60plus(0)
                .avgDwellSec(BigDecimal.ZERO)
                .dwellSumSec(BigDecimal.ZERO)
                .dwell1ToUnder2s(0).dwell2ToUnder3s(0).dwell3ToUnder4s(0).dwell4sAndOver(0);

        VisionSummary5s row = applyAgeGroup(builder, ageGroup, maleCount, femaleCount);
        return visionSummary5sRepository.save(row);
    }

    /** 빌더는 이미 모든 필드를 기본값(0)으로 채운 상태이므로, 대상 연령대의 male/female 컬럼만 재설정한다. */
    private VisionSummary5s applyAgeGroup(VisionSummary5s.VisionSummary5sBuilder builder, AgeGroup ageGroup, int maleCount, int femaleCount) {
        switch (ageGroup) {
            case UNDER_10 -> builder.otsMaleUnder10(maleCount).otsFemaleUnder10(femaleCount);
            case AGE_10S -> builder.otsMale10s(maleCount).otsFemale10s(femaleCount);
            case AGE_20S -> builder.otsMale20s(maleCount).otsFemale20s(femaleCount);
            case AGE_30S -> builder.otsMale30s(maleCount).otsFemale30s(femaleCount);
            case AGE_40S -> builder.otsMale40s(maleCount).otsFemale40s(femaleCount);
            case AGE_50S -> builder.otsMale50s(maleCount).otsFemale50s(femaleCount);
            case AGE_60_PLUS -> builder.otsMale60plus(maleCount).otsFemale60plus(femaleCount);
        }
        return builder.build();
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
