package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationResponse;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.exception.MediaErrorCode;
import com.shinhan.klljs.domain.media.service.MediaUnitCommandService;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
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
class CampaignRegistrationServiceTest {

    @Autowired
    private CampaignRegistrationService service;

    @Autowired
    private CampaignCreativeTokenService creativeTokenService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void register_storesVerifiedCreativeAndSelectedMedia() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER, MediaUnitStatus.ACTIVE);

        CampaignRegistrationResponse response = service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        );
        entityManager.flush();

        Campaign campaign = entityManager.find(Campaign.class, response.campaignId());
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.REGISTERED);
        assertThat(campaign.getCampaignName()).isEqualTo("여름 캠페인");
        assertThat(campaign.getMediaUnit().getId()).isEqualTo(fixture.mediaUnit().getId());
        assertThat(campaign.getCreativeType()).isEqualTo(CampaignCreativeType.IMAGE);
        assertThat(campaign.getCreativeStorageKey()).startsWith("campaign-creatives/");
        assertThat(campaign.getDescription()).isNull();
        assertThat(response.creativeUrl()).contains(campaign.getCreativeStorageKey());
    }

    @Test
    void register_rejectsMemberRole() {
        Fixture fixture = persistFixture(TeamMemberRole.MEMBER, MediaUnitStatus.ACTIVE);

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.CAMPAIGN_MANAGEMENT_FORBIDDEN));
    }

    @Test
    void register_hidesInactiveTeamStatusFromNonMember() {
        Fixture fixture = persistFixture(
                TeamMemberRole.OWNER,
                MediaUnitStatus.ACTIVE,
                TeamStatus.SUSPENDED,
                false
        );

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void register_rejectsSuspendedTeamForActiveMember() {
        Fixture fixture = persistFixture(
                TeamMemberRole.OWNER,
                MediaUnitStatus.ACTIVE,
                TeamStatus.SUSPENDED,
                true
        );

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_NOT_ACTIVE));
    }

    @Test
    void register_rejectsOverlappingCampaignIncludingBoundaryDate() {
        Fixture fixture = persistFixture(TeamMemberRole.ADMIN, MediaUnitStatus.ACTIVE);
        persistCampaign(fixture, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11));
        entityManager.flush();

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_PERIOD_CONFLICT));
    }

    @Test
    void register_rejectsInactiveMedia() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER, MediaUnitStatus.INACTIVE);

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), validRequest(fixture)
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MediaErrorCode.MEDIA_UNIT_NOT_ACTIVE));
    }

    @Test
    void register_rejectsCreativeTokenIssuedForAnotherUser() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER, MediaUnitStatus.ACTIVE);
        CampaignRegistrationRequest request = requestWithToken(
                fixture,
                creativeTokenService.issue(
                        999L,
                        CampaignCreativeType.IMAGE,
                        "campaign-creatives/999/other-user-object",
                        "poster.png"
                ).token()
        );

        assertThatThrownBy(() -> service.register(
                fixture.user().getId(), fixture.team().getId(), request
        )).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.INVALID_CREATIVE_TOKEN));
    }

    private CampaignRegistrationRequest validRequest(Fixture fixture) {
        String token = creativeTokenService.issue(
                fixture.user().getId(),
                CampaignCreativeType.IMAGE,
                "campaign-creatives/" + fixture.user().getId() + "/test-object",
                "poster.png"
        ).token();
        return requestWithToken(fixture, token);
    }

    private CampaignRegistrationRequest requestWithToken(Fixture fixture, String token) {
        return new CampaignRegistrationRequest(
                token,
                "  여름 캠페인  ",
                "  브랜드 A  ",
                "2026-07-11",
                "2026-07-12",
                100,
                "   ",
                fixture.mediaUnit().getId()
        );
    }

    private Fixture persistFixture(TeamMemberRole role, MediaUnitStatus mediaStatus) {
        return persistFixture(role, mediaStatus, TeamStatus.ACTIVE, true);
    }

    private Fixture persistFixture(
            TeamMemberRole role,
            MediaUnitStatus mediaStatus,
            TeamStatus teamStatus,
            boolean createMembership
    ) {
        Team team = Team.builder().teamName("캠페인 팀 " + System.nanoTime()).status(teamStatus).build();
        User user = User.builder().displayName("등록자").status(UserStatus.ACTIVE).build();
        entityManager.persist(team);
        entityManager.persist(user);
        if (createMembership) {
            entityManager.persist(TeamMember.builder()
                    .team(team)
                    .user(user)
                    .role(role)
                    .status(TeamMemberStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build());
        }

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
                .status(mediaStatus)
                .build();
        entityManager.persist(mediaUnit);
        entityManager.flush();
        return new Fixture(team, user, mediaUnit);
    }

    private void persistCampaign(Fixture fixture, LocalDate startDate, LocalDate endDate) {
        entityManager.persist(Campaign.builder()
                .team(fixture.team())
                .mediaUnit(fixture.mediaUnit())
                .createdBy(fixture.user())
                .campaignName("기존 캠페인")
                .brandName("기존 브랜드")
                .executionStartDate(startDate)
                .executionEndDate(endDate)
                .dailyTargetPlayCount(50)
                .creativeType(CampaignCreativeType.VIDEO)
                .creativeStorageKey("campaign-creatives/existing/video.mp4")
                .creativeOriginalFilename("video.mp4")
                .status(CampaignStatus.IN_EXECUTION)
                .build());
    }

    private record Fixture(Team team, User user, MediaUnit mediaUnit) {
    }
}
