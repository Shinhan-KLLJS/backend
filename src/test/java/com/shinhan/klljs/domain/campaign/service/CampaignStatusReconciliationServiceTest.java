package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignCreativeType;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignStatusReconciliationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void reconcile_changesRegisteredCampaignsToBeforeInAndAfterExecutionUsingKstDate() {
        Campaign before = campaign(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 15));
        Campaign inExecution = campaign(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 13));
        Campaign after = campaign(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 12));
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.findAllByStatusNot(CampaignStatus.REGISTRATION_FAILED))
                .thenReturn(List.of(before, inExecution, after));
        CampaignStatusReconciliationService service =
                new CampaignStatusReconciliationService(repository, FIXED_CLOCK);

        int changedCount = service.reconcile();

        assertThat(changedCount).isEqualTo(3);
        assertThat(before.getStatus()).isEqualTo(CampaignStatus.BEFORE_EXECUTION);
        assertThat(inExecution.getStatus()).isEqualTo(CampaignStatus.IN_EXECUTION);
        assertThat(after.getStatus()).isEqualTo(CampaignStatus.AFTER_EXECUTION);
    }

    @Test
    void reconcile_doesNotCountCampaignWhoseStatusIsAlreadyCorrect() {
        Campaign campaign = campaign(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 14));
        campaign.changeStatus(CampaignStatus.IN_EXECUTION);
        CampaignRepository repository = mock(CampaignRepository.class);
        when(repository.findAllByStatusNot(CampaignStatus.REGISTRATION_FAILED))
                .thenReturn(List.of(campaign));
        CampaignStatusReconciliationService service =
                new CampaignStatusReconciliationService(repository, FIXED_CLOCK);

        assertThat(service.reconcile()).isZero();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.IN_EXECUTION);
    }

    private Campaign campaign(LocalDate startDate, LocalDate endDate) {
        return Campaign.builder()
                .campaignName("상태 보정 테스트")
                .brandName("브랜드")
                .executionStartDate(startDate)
                .executionEndDate(endDate)
                .dailyTargetPlayCount(100)
                .creativeType(CampaignCreativeType.IMAGE)
                .creativeStorageKey("campaign-creatives/test/object")
                .creativeOriginalFilename("poster.png")
                .status(CampaignStatus.REGISTERED)
                .build();
    }
}
