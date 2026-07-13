package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.vision.VisionSummaryFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionSummaryIngestServiceTest {

    @Test
    void ingest_continuesAfterRetryableMediaFailureAndReturnsNoAckResult() {
        MediaUnit savedMedia = media(1L);
        MediaUnit noCampaignMedia = media(2L);
        MediaUnit failedMedia = media(3L);
        Campaign savedCampaign = campaign(101L);
        Campaign failedCampaign = campaign(103L);
        MediaUnitRepository mediaRepository = mock(MediaUnitRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        VisionSummaryItemWriter writer = mock(VisionSummaryItemWriter.class);
        when(mediaRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                "board_gangnam_01", "adscope-cam-01", MediaUnitStatus.ACTIVE
        )).thenReturn(List.of(savedMedia, noCampaignMedia, failedMedia));
        when(campaignRepository.findActiveCampaignsForMediaUnit(
                1L, LocalDate.of(2026, 7, 7), CampaignStatus.REGISTRATION_FAILED
        )).thenReturn(List.of(savedCampaign));
        when(campaignRepository.findActiveCampaignsForMediaUnit(
                2L, LocalDate.of(2026, 7, 7), CampaignStatus.REGISTRATION_FAILED
        )).thenReturn(List.of());
        when(campaignRepository.findActiveCampaignsForMediaUnit(
                3L, LocalDate.of(2026, 7, 7), CampaignStatus.REGISTRATION_FAILED
        )).thenReturn(List.of(failedCampaign));
        doThrow(new TransientDataAccessResourceException("temporary DB failure"))
                .when(writer).write(
                        eq(VisionSummaryFixtures.message()),
                        eq(VisionSummaryFixtures.RAW_BODY),
                        eq(failedMedia),
                        eq(failedCampaign),
                        any(LocalDateTime.class)
                );
        VisionSummaryIngestService service =
                new VisionSummaryIngestService(mediaRepository, campaignRepository, writer);

        VisionFanOutResult result =
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY);

        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.noCampaignCount()).isEqualTo(1);
        assertThat(result.retryableFailureCount()).isEqualTo(1);
        assertThat(result.processedMediaCount()).isEqualTo(3);
        assertThat(result.shouldAck()).isFalse();
        verify(writer).write(
                eq(VisionSummaryFixtures.message()),
                eq(VisionSummaryFixtures.RAW_BODY),
                eq(savedMedia),
                eq(savedCampaign),
                any(LocalDateTime.class)
        );
    }

    @Test
    void ingest_skipsAmbiguousCampaignWithoutArbitrarilyChoosingOne() {
        MediaUnit media = media(1L);
        Campaign firstCampaign = campaign(101L);
        Campaign secondCampaign = campaign(102L);
        MediaUnitRepository mediaRepository = mock(MediaUnitRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        VisionSummaryItemWriter writer = mock(VisionSummaryItemWriter.class);
        when(mediaRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                "board_gangnam_01", "adscope-cam-01", MediaUnitStatus.ACTIVE
        )).thenReturn(List.of(media));
        when(campaignRepository.findActiveCampaignsForMediaUnit(
                1L, LocalDate.of(2026, 7, 7), CampaignStatus.REGISTRATION_FAILED
        )).thenReturn(List.of(firstCampaign, secondCampaign));
        VisionSummaryIngestService service =
                new VisionSummaryIngestService(mediaRepository, campaignRepository, writer);

        VisionFanOutResult result =
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY);

        assertThat(result.ambiguousCampaignCount()).isEqualTo(1);
        assertThat(result.shouldAck()).isTrue();
        verify(writer, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void ingest_classifiesMediaTimeUniqueViolationAsDuplicate() {
        MediaUnit media = media(1L);
        Campaign campaign = campaign(101L);
        MediaUnitRepository mediaRepository = mock(MediaUnitRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        VisionSummaryItemWriter writer = mock(VisionSummaryItemWriter.class);
        when(mediaRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                "board_gangnam_01", "adscope-cam-01", MediaUnitStatus.ACTIVE
        )).thenReturn(List.of(media));
        when(campaignRepository.findActiveCampaignsForMediaUnit(
                1L, LocalDate.of(2026, 7, 7), CampaignStatus.REGISTRATION_FAILED
        )).thenReturn(List.of(campaign));
        doThrow(new DataIntegrityViolationException("UK_VISION_SUMMARY_MEDIA_TIME"))
                .when(writer).write(any(), any(), any(), any(), any());
        VisionSummaryIngestService service =
                new VisionSummaryIngestService(mediaRepository, campaignRepository, writer);

        VisionFanOutResult result =
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY);

        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.retryableFailureCount()).isZero();
        assertThat(result.shouldAck()).isTrue();
    }

    @Test
    void ingest_rejectsMessageWhenNoActiveMediaMappingExists() {
        MediaUnitRepository mediaRepository = mock(MediaUnitRepository.class);
        when(mediaRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                "board_gangnam_01", "adscope-cam-01", MediaUnitStatus.ACTIVE
        )).thenReturn(List.of());
        VisionSummaryIngestService service = new VisionSummaryIngestService(
                mediaRepository,
                mock(CampaignRepository.class),
                mock(VisionSummaryItemWriter.class)
        );

        assertThatThrownBy(() ->
                service.ingest(VisionSummaryFixtures.message(), VisionSummaryFixtures.RAW_BODY)
        ).isInstanceOf(VisionSummaryIngestService.MediaUnitNotFoundException.class);
    }

    private MediaUnit media(Long id) {
        MediaUnit media = mock(MediaUnit.class);
        when(media.getId()).thenReturn(id);
        return media;
    }

    private Campaign campaign(Long id) {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(id);
        return campaign;
    }
}
