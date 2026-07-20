package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.service.CampaignCreativeTokenService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CampaignRegistrationControllerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CampaignCreativeTokenService creativeTokenService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void register_returnsCreatedCampaignForAuthenticatedOwner() throws Exception {
        Fixture fixture = persistFixture();
        String creativeToken = creativeTokenService.issue(
                fixture.userId(),
                CampaignCreativeType.IMAGE,
                "campaign-creatives/" + fixture.userId() + "/controller-object",
                "poster.png"
        ).token();

        mockMvc.perform(post("/api/v1/teams/{teamId}/campaigns", fixture.teamId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.userId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(creativeToken, fixture.mediaUnitId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON_201_001"))
                .andExpect(jsonPath("$.result.teamId").value(fixture.teamId()))
                .andExpect(jsonPath("$.result.status").value("IN_EXECUTION"))
                .andExpect(jsonPath("$.result.creativeType").value("IMAGE"))
                .andExpect(jsonPath("$.result.mediaUnitId").value(fixture.mediaUnitId()));
    }

    @Test
    void register_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/teams/{teamId}/campaigns", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private Fixture persistFixture() {
        Team team = Team.builder().teamName("컨트롤러 팀").status(TeamStatus.ACTIVE).build();
        User user = User.builder().displayName("소유자").status(UserStatus.ACTIVE).build();
        entityManager.persist(team);
        entityManager.persist(user);
        entityManager.persist(TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.OWNER)
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
        entityManager.flush();
        return new Fixture(team.getId(), user.getId(), mediaUnit.getId());
    }

    private String requestBody(String creativeToken, Long mediaUnitId) {
        LocalDate today = LocalDate.now(clock.withZone(KST));

        return """
                {
                  "creativeToken": "%s",
                  "campaignName": "여름 캠페인",
                  "brandName": "브랜드 A",
                  "executionStartDate": "%s",
                  "executionEndDate": "%s",
                  "dailyTargetPlayCount": 100,
                  "description": "인지도 분석",
                  "mediaUnitId": %d
                }
                """.formatted(creativeToken, today, today.plusDays(1), mediaUnitId);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenService.generateAccessToken(userId);
    }

    private record Fixture(Long teamId, Long userId, Long mediaUnitId) {
    }
}
