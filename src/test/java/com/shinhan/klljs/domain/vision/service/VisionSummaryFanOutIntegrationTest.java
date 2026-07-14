package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.media.service.MediaUnitCommandService;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.domain.vision.VisionSummaryFixtures;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
class VisionSummaryFanOutIntegrationTest {

    @Autowired
    private VisionSummaryIngestService service;

    @Autowired
    private VisionSummary5sRepository visionRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private MediaUnitRepository mediaUnitRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        visionRepository.deleteAll();
        campaignRepository.deleteAll();
        mediaUnitRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ingest_savesSameSourceForEveryCampaignMediaAndTreatsRedeliveryAsDuplicate() {
        Team team = teamRepository.save(
                Team.builder().teamName("fan-out 팀").status(TeamStatus.ACTIVE).build()
        );
        User user = userRepository.save(
                User.builder().displayName("등록자").status(UserStatus.ACTIVE).build()
        );
        MediaUnit firstMedia = mediaUnitRepository.save(media("첫 번째 매체"));
        MediaUnit secondMedia = mediaUnitRepository.save(media("두 번째 매체"));
        Campaign firstCampaign = campaignRepository.save(campaign(team, user, firstMedia, "첫 캠페인"));
        Campaign secondCampaign = campaignRepository.save(campaign(team, user, secondMedia, "둘째 캠페인"));

        VisionFanOutResult first =
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY);
        VisionFanOutResult redelivery =
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY);

        assertThat(first.savedCount()).isEqualTo(2);
        assertThat(first.shouldAck()).isTrue();
        assertThat(redelivery.duplicateCount()).isEqualTo(2);
        assertThat(redelivery.shouldAck()).isTrue();
        assertThat(visionRepository.findAll()).hasSize(2)
                .extracting(row -> row.getCampaign().getId())
                .containsExactlyInAnyOrder(firstCampaign.getId(), secondCampaign.getId());
        assertThat(visionRepository.findAll())
                .allSatisfy(row -> {
                    assertThat(row.getOtsCount()).isEqualTo(10);
                    assertThat(row.getLtsCount()).isEqualTo(4);
                    assertThat(row.getDwellSumSec()).isEqualByComparingTo("10.0");
                });
    }

    private MediaUnit media(String name) {
        return MediaUnit.builder()
                .boardCode(MediaUnitCommandService.MVP_BOARD_CODE)
                .deviceCode(MediaUnitCommandService.MVP_DEVICE_CODE)
                .mediaName(name)
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
    }

    private Campaign campaign(Team team, User user, MediaUnit media, String name) {
        return Campaign.builder()
                .team(team)
                .mediaUnit(media)
                .createdBy(user)
                .campaignName(name)
                .brandName("브랜드")
                .executionStartDate(LocalDate.of(2026, 7, 7))
                .executionEndDate(LocalDate.of(2026, 7, 7))
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/" + name)
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.IN_EXECUTION)
                .build();
    }
}
