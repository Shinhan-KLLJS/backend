package com.shinhan.klljs.domain.media.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.media.dto.MediaRegionListResponse;
import com.shinhan.klljs.domain.media.dto.MediaUnitListResponse;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.team.entity.Team;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@Transactional
class MediaUnitQueryServiceTest {

    @Autowired
    private MediaUnitQueryService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getMediaUnits_treatsWildcardCharactersLiterallyAndMarksPeriodConflict() {
        MediaUnit percentMedia = persistMedia("100% 강남 전광판", "서울특별시", "강남구");
        persistMedia("일반 서초 전광판", "서울특별시", "서초구");
        persistCampaign(percentMedia, LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 12));
        entityManager.flush();

        MediaUnitListResponse response = service.getMediaUnits(
                "%",
                "서울특별시",
                "강남구",
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 7, 13),
                null,
                null
        );

        assertThat(response.mediaUnits()).singleElement().satisfies(media -> {
            assertThat(media.mediaUnitId()).isEqualTo(percentMedia.getId());
            assertThat(media.available()).isFalse();
            assertThat(media.unavailableReason()).isEqualTo("PERIOD_CONFLICT");
        });
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getMediaUnits_defaultsToFirstTenAndReportsHasMore() {
        // media_name 오름차순 정렬이라 "매체 01".."매체 12"는 그 순서 그대로 정렬된다.
        for (int i = 1; i <= 12; i++) {
            persistMedia("매체 %02d".formatted(i), "서울특별시", "강남구");
        }
        entityManager.flush();

        MediaUnitListResponse response = service.getMediaUnits(
                null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), null, null
        );

        assertThat(response.mediaUnits()).hasSize(10);
        assertThat(response.mediaUnits().get(0).mediaName()).isEqualTo("매체 01");
        assertThat(response.mediaUnits().get(9).mediaName()).isEqualTo("매체 10");
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void getMediaUnits_secondPageWithOffsetReturnsRemainingItemsAndHasMoreFalse() {
        for (int i = 1; i <= 12; i++) {
            persistMedia("매체 %02d".formatted(i), "서울특별시", "강남구");
        }
        entityManager.flush();

        MediaUnitListResponse response = service.getMediaUnits(
                null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), 10, 6
        );

        assertThat(response.mediaUnits()).hasSize(2);
        assertThat(response.mediaUnits().get(0).mediaName()).isEqualTo("매체 11");
        assertThat(response.mediaUnits().get(1).mediaName()).isEqualTo("매체 12");
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getMediaUnits_limitAboveMaxIsClampedTo50() {
        for (int i = 1; i <= 60; i++) {
            persistMedia("매체 %02d".formatted(i), "서울특별시", "강남구");
        }
        entityManager.flush();

        MediaUnitListResponse response = service.getMediaUnits(
                null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), null, 1000
        );

        assertThat(response.mediaUnits()).hasSize(50);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void getRegions_returnsSortedDistinctValuesFromActiveMediaOnly() {
        persistMedia("해운대 A", "부산광역시", "해운대구");
        persistMedia("강남 A", "서울특별시", "강남구");
        persistMedia("강남 B", "서울특별시", "강남구");
        MediaUnit inactive = persistMedia("서초 비활성", "서울특별시", "서초구");
        inactive.changeStatus(MediaUnitStatus.INACTIVE);
        entityManager.flush();

        MediaRegionListResponse response = service.getRegions();

        assertThat(response.regions()).extracting("sido")
                .containsExactly("부산광역시", "서울특별시");
        assertThat(response.regions().get(1).sigungu()).containsExactly("강남구");
    }

    private MediaUnit persistMedia(String name, String sido, String sigungu) {
        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode(MediaUnitCommandService.MVP_BOARD_CODE)
                .deviceCode(MediaUnitCommandService.MVP_DEVICE_CODE)
                .mediaName(name)
                .photoUrl("https://cdn.example.com/media.jpg")
                .locationAddress(sido + " " + sigungu)
                .sido(sido)
                .sigungu(sigungu)
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
        return mediaUnit;
    }

    private void persistCampaign(MediaUnit mediaUnit, LocalDate startDate, LocalDate endDate) {
        Team team = Team.builder().teamName("광고팀").status(TeamStatus.ACTIVE).build();
        User user = User.builder().displayName("등록자").status(UserStatus.ACTIVE).build();
        entityManager.persist(team);
        entityManager.persist(user);

        Campaign campaign = Campaign.builder()
                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(user)
                .campaignName("기간 충돌 캠페인")
                .brandName("브랜드")
                .executionStartDate(startDate)
                .executionEndDate(endDate)
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/object")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.AFTER_EXECUTION)
                .build();
        entityManager.persist(campaign);
    }
}
