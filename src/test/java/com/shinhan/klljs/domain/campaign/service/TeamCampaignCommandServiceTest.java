package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.CampaignRenameResponse;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
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
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
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
class TeamCampaignCommandServiceTest {

    @Autowired
    private TeamCampaignCommandService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deleteCampaign_ownerCanDeleteAndRowIsActuallyRemoved() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        service.deleteCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId());
        entityManager.flush();

        assertThat(entityManager.find(Campaign.class, fixture.campaignId())).isNull();
    }

    @Test
    void deleteCampaign_adminCanDelete() {
        Fixture fixture = persistFixture(TeamMemberRole.ADMIN);

        service.deleteCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId());
        entityManager.flush();

        assertThat(entityManager.find(Campaign.class, fixture.campaignId())).isNull();
    }

    @Test
    void deleteCampaign_memberIsForbidden() {
        Fixture fixture = persistFixture(TeamMemberRole.MEMBER);

        assertThatThrownBy(() -> service.deleteCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.CAMPAIGN_MANAGEMENT_FORBIDDEN));
        entityManager.flush();
        assertThat(entityManager.find(Campaign.class, fixture.campaignId())).isNotNull();
    }

    @Test
    void deleteCampaign_throwsAccessDeniedForNonMember() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);
        User outsider = User.builder().displayName("외부인").status(UserStatus.ACTIVE).build();
        entityManager.persist(outsider);
        entityManager.flush();

        assertThatThrownBy(() -> service.deleteCampaign(outsider.getId(), fixture.teamId(), fixture.campaignId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void deleteCampaign_throwsTeamNotFoundForNonExistentTeam() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() -> service.deleteCampaign(fixture.userId(), 999_999L, fixture.campaignId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_NOT_FOUND));
    }

    @Test
    void deleteCampaign_throwsCampaignNotFoundWhenCampaignDoesNotExist() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() -> service.deleteCampaign(fixture.userId(), fixture.teamId(), 999_999L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    @Test
    void deleteCampaign_throwsCampaignNotFoundWhenCampaignBelongsToDifferentTeam() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);
        Fixture otherTeam = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.deleteCampaign(fixture.userId(), fixture.teamId(), otherTeam.campaignId()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    @Test
    void deleteCampaign_leavesReferencingVisionSummaryRowWithCampaignIdSetToNull() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);
        VisionSummary5s summary = persistVisionSummary(fixture);
        entityManager.flush();
        // 영속성 컨텍스트를 비워서, 서비스가 findById로 새로 읽어온 Campaign 인스턴스와
        // 여기서 getReference()로 만든 프록시가 같은 세션에서 충돌하는 걸 막는다.
        entityManager.clear();

        service.deleteCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId());
        entityManager.flush();
        entityManager.clear();

        VisionSummary5s reloaded = entityManager.find(VisionSummary5s.class, summary.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getCampaign()).isNull();
    }

    @Test
    void renameCampaign_ownerCanRename() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        CampaignRenameResponse response = service.renameCampaign(
                fixture.userId(), fixture.teamId(), fixture.campaignId(), "새 캠페인명");
        entityManager.flush();

        assertThat(response.campaignId()).isEqualTo(fixture.campaignId());
        assertThat(response.campaignName()).isEqualTo("새 캠페인명");
        assertThat(entityManager.find(Campaign.class, fixture.campaignId()).getCampaignName())
                .isEqualTo("새 캠페인명");
    }

    @Test
    void renameCampaign_adminCanRename() {
        Fixture fixture = persistFixture(TeamMemberRole.ADMIN);

        service.renameCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId(), "관리자가 바꾼 캠페인명");
        entityManager.flush();

        assertThat(entityManager.find(Campaign.class, fixture.campaignId()).getCampaignName())
                .isEqualTo("관리자가 바꾼 캠페인명");
    }

    @Test
    void renameCampaign_memberIsForbidden() {
        Fixture fixture = persistFixture(TeamMemberRole.MEMBER);
        String originalName = entityManager.find(Campaign.class, fixture.campaignId()).getCampaignName();

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId(), "멤버가 시도한 캠페인명"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.CAMPAIGN_MANAGEMENT_FORBIDDEN));
        entityManager.flush();
        assertThat(entityManager.find(Campaign.class, fixture.campaignId()).getCampaignName())
                .isEqualTo(originalName);
    }

    @Test
    void renameCampaign_throwsAccessDeniedForNonMember() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);
        User outsider = User.builder().displayName("외부인").status(UserStatus.ACTIVE).build();
        entityManager.persist(outsider);
        entityManager.flush();

        assertThatThrownBy(() ->
                service.renameCampaign(outsider.getId(), fixture.teamId(), fixture.campaignId(), "새 캠페인명"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    @Test
    void renameCampaign_throwsTeamNotFoundForNonExistentTeam() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), 999_999L, fixture.campaignId(), "새 캠페인명"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_NOT_FOUND));
    }

    @Test
    void renameCampaign_throwsCampaignNotFoundWhenCampaignDoesNotExist() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), fixture.teamId(), 999_999L, "새 캠페인명"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    @Test
    void renameCampaign_throwsCampaignNotFoundWhenCampaignBelongsToDifferentTeam() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);
        Fixture otherTeam = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), fixture.teamId(), otherTeam.campaignId(), "새 캠페인명"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    @Test
    void renameCampaign_rejectsBlankName() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId(), "   "))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST));
    }

    @Test
    void renameCampaign_rejectsNameOverMaxLength() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        assertThatThrownBy(() ->
                service.renameCampaign(fixture.userId(), fixture.teamId(), fixture.campaignId(), "A".repeat(31)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST));
    }

    @Test
    void renameCampaign_trimsEdgeWhitespace() {
        Fixture fixture = persistFixture(TeamMemberRole.OWNER);

        CampaignRenameResponse response = service.renameCampaign(
                fixture.userId(), fixture.teamId(), fixture.campaignId(), "  공백 캠페인명  ");

        assertThat(response.campaignName()).isEqualTo("공백 캠페인명");
    }

    private VisionSummary5s persistVisionSummary(Fixture fixture) {
        Campaign campaign = entityManager.getReference(Campaign.class, fixture.campaignId());
        MediaUnit mediaUnit = entityManager.getReference(MediaUnit.class, fixture.mediaUnitId());
        VisionSummary5s summary = VisionSummary5s.builder()
                .mediaUnit(mediaUnit)
                .campaign(campaign)
                .deviceId(MediaUnitCommandService.MVP_DEVICE_CODE)
                .boardId(MediaUnitCommandService.MVP_BOARD_CODE)
                .seq(1L)
                .eventTime(LocalDateTime.of(2026, 7, 7, 2, 19, 20))
                .intervalSec(new BigDecimal("5.000"))
                .receivedAt(LocalDateTime.now())
                .rawPayload("{}")
                .otsCount(0).ltsCount(0)
                .otsMaleCount(0).otsFemaleCount(0).ltsMaleCount(0).ltsFemaleCount(0)
                .otsMaleUnder10(0).otsMale10s(0).otsMale20s(0).otsMale30s(0).otsMale40s(0).otsMale50s(0).otsMale60plus(0)
                .otsFemaleUnder10(0).otsFemale10s(0).otsFemale20s(0).otsFemale30s(0).otsFemale40s(0).otsFemale50s(0).otsFemale60plus(0)
                .ltsMaleUnder10(0).ltsMale10s(0).ltsMale20s(0).ltsMale30s(0).ltsMale40s(0).ltsMale50s(0).ltsMale60plus(0)
                .ltsFemaleUnder10(0).ltsFemale10s(0).ltsFemale20s(0).ltsFemale30s(0).ltsFemale40s(0).ltsFemale50s(0).ltsFemale60plus(0)
                .avgDwellSec(BigDecimal.ZERO).dwellSumSec(BigDecimal.ZERO)
                .dwell1ToUnder2s(0).dwell2ToUnder3s(0).dwell3ToUnder4s(0).dwell4sAndOver(0)
                .build();
        entityManager.persist(summary);
        return summary;
    }

    private Fixture persistFixture(TeamMemberRole role) {
        Team team = Team.builder().teamName("삭제 테스트 팀 " + System.nanoTime()).status(TeamStatus.ACTIVE).build();
        User user = User.builder().displayName("요청자").status(UserStatus.ACTIVE).build();
        entityManager.persist(team);
        entityManager.persist(user);
        entityManager.persist(TeamMember.builder()
                .team(team)
                .user(user)
                .role(role)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());

        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode(MediaUnitCommandService.MVP_BOARD_CODE)
                .deviceCode(MediaUnitCommandService.MVP_DEVICE_CODE)
                .mediaName("삭제 테스트 매체")
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
                .build();
        entityManager.persist(mediaUnit);

        LocalDate today = LocalDate.now();
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName("삭제 대상 캠페인 " + System.nanoTime())
                .brandName("브랜드")
                .executionStartDate(today.minusDays(1))
                .executionEndDate(today.plusDays(1))
                .dailyTargetPlayCount(200)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/delete-target")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build();
        entityManager.persist(campaign);
        entityManager.flush();

        return new Fixture(team.getId(), user.getId(), campaign.getId(), mediaUnit.getId());
    }

    private record Fixture(Long teamId, Long userId, Long campaignId, Long mediaUnitId) {
    }
}
