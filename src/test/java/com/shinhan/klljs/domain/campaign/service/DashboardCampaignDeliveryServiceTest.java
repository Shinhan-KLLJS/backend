package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDeliveryResponse;
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

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@Transactional
class DashboardCampaignDeliveryServiceTest {

    @Autowired
    private DashboardCampaignDeliveryService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getDelivery_registrationFailedCurrentPlayCountIsAlwaysZeroEvenIfPeriodOverlapsToday() {
        LocalDate today = LocalDate.now();
        Team team = Team.builder().teamName("송출정보 팀 " + System.nanoTime()).status(TeamStatus.ACTIVE).build();
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
                .mediaName("송출정보 매체")
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

        // 실행 기간이 오늘을 포함하도록 잡아도, 등록 실패는 실제로 송출된 적이 없으므로 0이어야 한다.
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName("등록 실패 캠페인")
                .brandName("브랜드")
                .executionStartDate(today.minusDays(1))
                .executionEndDate(today.plusDays(1))
                .dailyTargetPlayCount(200)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/registration-failed")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.REGISTRATION_FAILED)
                .build();
        entityManager.persist(campaign);
        entityManager.flush();

        DashboardCampaignDeliveryResponse response = service.getDelivery(
                user.getId(), campaign.getId(), today.minusDays(1), today.plusDays(1)
        );

        assertThat(response.currentPlayCount()).isZero();
        assertThat(response.nextIncrementAt()).isNull();
        assertThat(response.progressRate()).isZero();
        assertThat(response.totalPlayTimeMin()).isZero();
    }
}
