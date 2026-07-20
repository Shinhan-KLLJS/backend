package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.TeamCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSort;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignStatusFilter;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSummary;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.service.MediaUnitCommandService;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.code.GeneralErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.KstDateTimes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@Transactional
class TeamCampaignQueryServiceTest {

    @Autowired
    private TeamCampaignQueryService service;

    @Autowired
    private EntityManager entityManager;

    private static final LocalDate TODAY = LocalDate.now(KstDateTimes.KST);

    @Test
    void getCampaigns_returnsAllCampaignsSortedByNameByDefault() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "나이키 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(2), TODAY.plusDays(2));
        persistCampaign(fixture, "아디다스 캠페인", CampaignStatus.BEFORE_EXECUTION, TODAY.plusDays(5), TODAY.plusDays(10));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.teamName()).isEqualTo(fixture.teamName());
        assertThat(response.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("나이키 캠페인", "아디다스 캠페인");
    }

    @Test
    void getCampaigns_filtersByKeywordCaseInsensitive() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "Nike Summer", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        persistCampaign(fixture, "아디다스 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        TeamCampaignListResponse response =
                service.getCampaigns(fixture.userId(), fixture.teamId(), null, "nike", null);

        assertThat(response.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("Nike Summer");
    }

    @Test
    void getCampaigns_beforeExecutionFilterAlsoIncludesRegistered() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "등록 직후", CampaignStatus.REGISTERED, TODAY.plusDays(3), TODAY.plusDays(8));
        persistCampaign(fixture, "집행 예정", CampaignStatus.BEFORE_EXECUTION, TODAY.plusDays(5), TODAY.plusDays(10));
        persistCampaign(fixture, "집행 중", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(
                fixture.userId(), fixture.teamId(), TeamCampaignStatusFilter.BEFORE_EXECUTION, null, null);

        assertThat(response.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactlyInAnyOrder("등록 직후", "집행 예정");
    }

    @Test
    void getCampaigns_inExecutionFilterExcludesOtherStatuses() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "집행 중", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        persistCampaign(fixture, "집행 예정", CampaignStatus.BEFORE_EXECUTION, TODAY.plusDays(5), TODAY.plusDays(10));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(
                fixture.userId(), fixture.teamId(), TeamCampaignStatusFilter.IN_EXECUTION, null, null);

        assertThat(response.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("집행 중");
    }

    @Test
    void getCampaigns_afterExecutionFilterOnlyMatchesEndedCampaigns() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "종료됨", CampaignStatus.AFTER_EXECUTION, TODAY.minusDays(10), TODAY.minusDays(5));
        persistCampaign(fixture, "집행 중", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(
                fixture.userId(), fixture.teamId(), TeamCampaignStatusFilter.AFTER_EXECUTION, null, null);

        assertThat(response.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("종료됨");
    }

    @Test
    void getCampaigns_sortsByExecutionRecentAndOldest() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "일찍 시작", CampaignStatus.IN_EXECUTION, TODAY.minusDays(5), TODAY.plusDays(5));
        persistCampaign(fixture, "늦게 시작", CampaignStatus.BEFORE_EXECUTION, TODAY.plusDays(10), TODAY.plusDays(20));
        entityManager.flush();

        TeamCampaignListResponse recent = service.getCampaigns(
                fixture.userId(), fixture.teamId(), null, null, TeamCampaignSort.EXECUTION_RECENT);
        TeamCampaignListResponse oldest = service.getCampaigns(
                fixture.userId(), fixture.teamId(), null, null, TeamCampaignSort.EXECUTION_OLDEST);

        assertThat(recent.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("늦게 시작", "일찍 시작");
        assertThat(oldest.campaigns()).extracting(TeamCampaignSummary::campaignName)
                .containsExactly("일찍 시작", "늦게 시작");
    }

    @Test
    void getCampaigns_todayPlayCountIsZeroBeforeExecution() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "집행 예정", CampaignStatus.BEFORE_EXECUTION, TODAY.plusDays(5), TODAY.plusDays(10));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.campaigns().get(0).todayPlayCount()).isZero();
        assertThat(response.campaigns().get(0).dailyTargetPlayCount()).isEqualTo(200);
    }

    @Test
    void getCampaigns_todayPlayCountEqualsDailyTargetAfterExecution() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "종료됨", CampaignStatus.AFTER_EXECUTION, TODAY.minusDays(10), TODAY.minusDays(5));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.campaigns().get(0).todayPlayCount()).isEqualTo(200);
    }

    @Test
    void getCampaigns_todayPlayCountIsWithinExpectedBoundsDuringExecution() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "집행 중", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        LocalDateTime before = KstDateTimes.toKst(LocalDateTime.now(java.time.ZoneOffset.UTC));
        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);
        LocalDateTime after = KstDateTimes.toKst(LocalDateTime.now(java.time.ZoneOffset.UTC));

        int lowerBound = elapsedIntervals(before);
        int upperBound = elapsedIntervals(after);
        int actual = response.campaigns().get(0).todayPlayCount();

        assertThat(actual).isBetween(lowerBound, Math.max(upperBound, lowerBound));
        assertThat(actual).isLessThanOrEqualTo(200);
    }

    private int elapsedIntervals(LocalDateTime nowKst) {
        LocalDateTime todayStart = TODAY.atTime(6, 0, 0);
        long elapsedSec = Math.max(0, java.time.Duration.between(todayStart, nowKst).getSeconds());
        return (int) Math.min(elapsedSec / 15, 200);
    }

    @Test
    void getCampaigns_registrationFailedTodayPlayCountIsAlwaysZeroEvenIfDatesOverlapToday() {
        Fixture fixture = persistFixture();
        // 실행 기간이 오늘을 포함하도록 잡아도, 등록 실패는 실제로 송출된 적이 없으므로 0이어야 한다.
        persistCampaign(fixture, "등록 실패", CampaignStatus.REGISTRATION_FAILED, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.campaigns()).hasSize(1);
        assertThat(response.campaigns().get(0).todayPlayCount()).isZero();
    }

    @Test
    void getCampaigns_throwsValidationErrorWhenKeywordExceeds100Chars() {
        Fixture fixture = persistFixture();
        String tooLongKeyword = "가".repeat(101);

        assertThatThrownBy(() -> service.getCampaigns(fixture.userId(), fixture.teamId(), null, tooLongKeyword, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.VALIDATION_ERROR));
    }

    @Test
    void getCampaigns_includesMediaLocationAddress() {
        Fixture fixture = persistFixture();
        persistCampaign(fixture, "나이키 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.campaigns().get(0).mediaLocationAddress()).isEqualTo("서울 강남구 영동대로 506");
    }

    @Test
    void getCampaigns_returnsEmptyArrayWhenTeamHasNoCampaigns() {
        Fixture fixture = persistFixture();
        entityManager.flush();

        TeamCampaignListResponse response = service.getCampaigns(fixture.userId(), fixture.teamId(), null, null, null);

        assertThat(response.campaigns()).isEmpty();
    }

    @Test
    void getCampaigns_throwsTeamNotFoundForNonExistentTeam() {
        Fixture fixture = persistFixture();

        assertThatThrownBy(() -> service.getCampaigns(fixture.userId(), 999_999L, null, null, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_NOT_FOUND));
    }

    @Test
    void getCampaigns_throwsAccessDeniedForNonMember() {
        Fixture fixture = persistFixture();
        User outsider = User.builder().displayName("외부인").status(UserStatus.ACTIVE).build();
        entityManager.persist(outsider);
        entityManager.flush();

        assertThatThrownBy(() -> service.getCampaigns(outsider.getId(), fixture.teamId(), null, null, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void getCampaignDetail_returnsFullDetailsForAccessibleCampaign() {
        Fixture fixture = persistFixture();
        Campaign campaign = persistCampaign(
                fixture, "나이키 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        var detail = service.getCampaignDetail(fixture.userId(), fixture.teamId(), campaign.getId());

        assertThat(detail.campaignId()).isEqualTo(campaign.getId());
        assertThat(detail.campaignName()).isEqualTo("나이키 캠페인");
        assertThat(detail.brandName()).isEqualTo("브랜드");
        assertThat(detail.executionStartDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(detail.executionEndDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(detail.dailyTargetPlayCount()).isEqualTo(200);
        assertThat(detail.description()).isEqualTo("메모");
        assertThat(detail.creativeType()).isEqualTo(CampaignCreativeType.IMAGE);
        assertThat(detail.creativeUrl()).contains(campaign.getCreativeStorageKey());
        assertThat(detail.mediaUnitId()).isEqualTo(fixture.mediaUnit().getId());
        assertThat(detail.mediaName()).isEqualTo("테스트 매체");
        assertThat(detail.mediaPhotoUrl()).isEqualTo("https://cdn.example.com/media.jpg");
        assertThat(detail.mediaLocationAddress()).isEqualTo("서울 강남구 영동대로 506");
        assertThat(detail.mediaWidthMm()).isEqualTo(1000);
        assertThat(detail.mediaHeightMm()).isEqualTo(500);
        assertThat(detail.mediaResolutionWidthPx()).isEqualTo(1920);
        assertThat(detail.mediaResolutionHeightPx()).isEqualTo(1080);
        assertThat(detail.mediaShapeTypes()).containsExactly(com.shinhan.klljs.domain.media.entity.MediaUnitShapeType.FLAT);
    }

    @Test
    void getCampaignDetail_throwsTeamNotFoundForNonExistentTeam() {
        Fixture fixture = persistFixture();
        Campaign campaign = persistCampaign(
                fixture, "나이키 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        assertThatThrownBy(() -> service.getCampaignDetail(fixture.userId(), 999_999L, campaign.getId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_NOT_FOUND));
    }

    @Test
    void getCampaignDetail_throwsAccessDeniedForNonMember() {
        Fixture fixture = persistFixture();
        Campaign campaign = persistCampaign(
                fixture, "나이키 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        User outsider = User.builder().displayName("외부인").status(UserStatus.ACTIVE).build();
        entityManager.persist(outsider);
        entityManager.flush();

        assertThatThrownBy(() -> service.getCampaignDetail(outsider.getId(), fixture.teamId(), campaign.getId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void getCampaignDetail_throwsCampaignNotFoundWhenCampaignDoesNotExist() {
        Fixture fixture = persistFixture();

        assertThatThrownBy(() -> service.getCampaignDetail(fixture.userId(), fixture.teamId(), 999_999L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    @Test
    void getCampaignDetail_throwsCampaignNotFoundWhenCampaignBelongsToDifferentTeam() {
        Fixture fixture = persistFixture();
        Fixture otherTeam = persistFixture();
        Campaign otherCampaign = persistCampaign(
                otherTeam, "다른 팀 캠페인", CampaignStatus.IN_EXECUTION, TODAY.minusDays(1), TODAY.plusDays(1));
        entityManager.flush();

        assertThatThrownBy(() -> service.getCampaignDetail(fixture.userId(), fixture.teamId(), otherCampaign.getId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private Fixture persistFixture() {
        Team team = Team.builder().teamName("캠페인 목록 팀 " + System.nanoTime()).status(TeamStatus.ACTIVE).build();
        User user = User.builder().displayName("조회자").status(UserStatus.ACTIVE).build();
        entityManager.persist(team);
        entityManager.persist(user);
        entityManager.persist(TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.MEMBER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());

        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode(MediaUnitCommandService.MVP_BOARD_CODE)
                .deviceCode(MediaUnitCommandService.MVP_DEVICE_CODE)
                .mediaName("테스트 매체")
                .photoUrl("https://cdn.example.com/media.jpg")
                .locationAddress("서울 강남구 영동대로 506")
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
                .build();
        entityManager.persist(mediaUnit);
        entityManager.flush();

        return new Fixture(team.getId(), team.getTeamName(), user.getId(), mediaUnit);
    }

    private Campaign persistCampaign(
            Fixture fixture, String campaignName, CampaignStatus status,
            LocalDate executionStartDate, LocalDate executionEndDate
    ) {
        Team team = entityManager.getReference(Team.class, fixture.teamId());
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(fixture.mediaUnit())
                .createdBy(entityManager.getReference(com.shinhan.klljs.domain.user.entity.User.class, fixture.userId()))
                .campaignName(campaignName)
                .brandName("브랜드")
                .executionStartDate(executionStartDate)
                .executionEndDate(executionEndDate)
                .dailyTargetPlayCount(200)
                .description("메모")
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/" + campaignName)
                .creativeOriginalFilename("poster.png")
                .status(status)
                .build();
        entityManager.persist(campaign);
        return campaign;
    }

    private record Fixture(Long teamId, String teamName, Long userId, MediaUnit mediaUnit) {
    }
}
