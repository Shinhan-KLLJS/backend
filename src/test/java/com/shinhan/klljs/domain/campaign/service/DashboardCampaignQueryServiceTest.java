package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDetailResponse;
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
    }

    @Test
    void getCampaignDetail_computesImageUrlFromCreativeStorageKey_andIncludesMediaPhotoUrl() {
        // 등록 시점에 image_url을 별도로 저장하지 않는다 - creativeStorageKey로 매 요청마다 계산해야
        // 실제 등록 플로우가 image_url을 채우는 걸 빠뜨려도(과거 버그) 절대 null이 될 수 없다.
        MediaUnit mediaUnit = persistMediaUnit("https://cdn.example.com/media-units/1.jpg");
        Campaign campaign = persistCampaign(mediaUnit, "campaign-creatives/1/abc123");
        entityManager.flush();

        DashboardCampaignDetailResponse detail = service.getCampaignDetail(
                userId, campaign.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThat(detail.imageUrl()).endsWith("campaign-creatives/1/abc123");
        assertThat(detail.mediaPhotoUrl()).isEqualTo("https://cdn.example.com/media-units/1.jpg");
        assertThat(detail.mediaUnitId()).isEqualTo(mediaUnit.getId());
    }

    private MediaUnit persistMediaUnit(String photoUrl) {
        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode("board-" + System.nanoTime()).deviceCode("device-" + System.nanoTime())
                .mediaName("매체")
                .photoUrl(photoUrl)
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
        return mediaUnit;
    }

    private Campaign persistCampaign(MediaUnit mediaUnit, String creativeStorageKey) {
        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName("캠페인")
                .brandName("브랜드")
                .executionStartDate(LocalDate.of(2026, 7, 1))
                .executionEndDate(LocalDate.of(2026, 7, 31))
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey(creativeStorageKey)
                .creativeOriginalFilename("test.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build();
        entityManager.persist(campaign);
        return campaign;
    }
}
