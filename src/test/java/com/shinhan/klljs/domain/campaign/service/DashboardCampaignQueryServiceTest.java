package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignSummary;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DashboardCampaignQueryServiceTest {

    @Autowired
    private DashboardCampaignQueryService service;

    @Autowired
    private EntityManager entityManager;

    private Long userId;
    private Team team;
    private User user;
    private MediaUnit mediaUnit;

    @BeforeEach
    void setUp() {
        team = Team.builder().teamName("팀 " + System.nanoTime()).status(TeamStatus.ACTIVE).build();
        entityManager.persist(team);

        user = User.builder().displayName("사용자").status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        userId = user.getId();

        entityManager.persist(TeamMember.builder()
                .team(team).user(user).role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());

        mediaUnit = MediaUnit.builder()
                .boardCode("board-" + System.nanoTime()).deviceCode("device-" + System.nanoTime())
                .mediaName("매체")
                .photoUrl("https://example.com/media.png")
                .locationAddress("서울시 어딘가")
                .sido("서울특별시").sigungu("강남구")
                .latitude(new BigDecimal("37.5000000"))
                .longitude(new BigDecimal("127.0000000"))
                .widthMm(1200).heightMm(800)
                .resolutionWidthPx(1920).resolutionHeightPx(1080)
                .shapeTypes(List.of(MediaUnitShapeType.FLAT))
                .status(MediaUnitStatus.ACTIVE)
                .build();
        entityManager.persist(mediaUnit);
    }

    @Test
    void getCampaigns_excludesRegistrationFailedRegisteredAndBeforeExecution() {
        persistCampaign("실패", CampaignStatus.REGISTRATION_FAILED);
        persistCampaign("등록완료", CampaignStatus.REGISTERED);
        persistCampaign("집행전", CampaignStatus.BEFORE_EXECUTION);
        Campaign inExecution = persistCampaign("집행중", CampaignStatus.IN_EXECUTION);
        Campaign afterExecution = persistCampaign("집행후", CampaignStatus.AFTER_EXECUTION);
        entityManager.flush();

        DashboardCampaignListResponse response = service.getCampaigns(userId, null, null);

        assertThat(response.campaigns()).extracting(DashboardCampaignSummary::campaignId)
                .containsExactlyInAnyOrder(inExecution.getId(), afterExecution.getId());
    }

    @Test
    void getCampaigns_includesStatusFieldInResponse() {
        persistCampaign("집행중", CampaignStatus.IN_EXECUTION);
        entityManager.flush();

        DashboardCampaignListResponse response = service.getCampaigns(userId, null, null);

        assertThat(response.campaigns()).hasSize(1);
        assertThat(response.campaigns().getFirst().status()).isEqualTo(CampaignStatus.IN_EXECUTION);
    }

    @Test
    void getCampaigns_defaultSelectionPrefersInExecutionOverAfterExecution() {
        persistCampaign("집행후", CampaignStatus.AFTER_EXECUTION);
        Campaign inExecution = persistCampaign("집행중", CampaignStatus.IN_EXECUTION);
        entityManager.flush();

        DashboardCampaignListResponse response = service.getCampaigns(userId, null, null);

        assertThat(response.campaigns())
                .filteredOn(DashboardCampaignSummary::defaultSelected)
                .extracting(DashboardCampaignSummary::campaignId)
                .containsExactly(inExecution.getId());
    }

    @Test
    void getCampaigns_defaultSelectionFallsBackToLatestEndedAfterExecution() {
        persistCampaign("먼저종료", CampaignStatus.AFTER_EXECUTION,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10));
        Campaign laterEnded = persistCampaign("나중종료", CampaignStatus.AFTER_EXECUTION,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20));
        entityManager.flush();

        DashboardCampaignListResponse response = service.getCampaigns(userId, null, null);

        assertThat(response.campaigns())
                .filteredOn(DashboardCampaignSummary::defaultSelected)
                .extracting(DashboardCampaignSummary::campaignId)
                .containsExactly(laterEnded.getId());
    }

    private Campaign persistCampaign(String name, CampaignStatus status) {
        return persistCampaign(name, status, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    private Campaign persistCampaign(String name, CampaignStatus status, LocalDate startDate, LocalDate endDate) {
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName(name)
                .brandName("브랜드")
                .executionStartDate(startDate)
                .executionEndDate(endDate)
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test.png")
                .creativeOriginalFilename("test.png")
                .status(status)
                .build();
        entityManager.persist(campaign);
        return campaign;
    }
}
