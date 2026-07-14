package com.shinhan.klljs.domain.campaign.controller;

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
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@AutoConfigureMockMvc
@Transactional
class TeamCampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getCampaigns_returnsTeamCampaignsForAuthenticatedMember() throws Exception {
        Fixture fixture = persistFixture();

        mockMvc.perform(get("/api/v1/teams/{teamId}/campaigns", fixture.teamId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_001"))
                .andExpect(jsonPath("$.result.teamName").value(fixture.teamName()))
                .andExpect(jsonPath("$.result.campaigns[0].campaignName").value("나이키 캠페인"))
                .andExpect(jsonPath("$.result.campaigns[0].mediaLocationAddress").value("서울 강남구"));
    }

    @Test
    void getCampaigns_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/teams/{teamId}/campaigns", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCampaigns_returns404WhenTeamDoesNotExist() throws Exception {
        Fixture fixture = persistFixture();

        mockMvc.perform(get("/api/v1/teams/{teamId}/campaigns", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.userId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_404_001"));
    }

    private Fixture persistFixture() {
        Team team = Team.builder().teamName("컨트롤러 캠페인 목록 팀").status(TeamStatus.ACTIVE).build();
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
                .mediaName("컨트롤러 매체")
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
        entityManager.persist(Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName("나이키 캠페인")
                .brandName("나이키")
                .executionStartDate(today.minusDays(1))
                .executionEndDate(today.plusDays(1))
                .dailyTargetPlayCount(200)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/controller-test/object")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build());
        entityManager.flush();

        return new Fixture(team.getId(), team.getTeamName(), user.getId());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenService.generateAccessToken(userId);
    }

    private record Fixture(Long teamId, String teamName, Long userId) {
    }
}
