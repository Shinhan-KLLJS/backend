package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 공용 Vision 메시지 한 건을 같은 board/device 코드를 사용하는 모든 ACTIVE 매체로 fan-out한다.
 *
 * <p>이 반복 메서드에는 트랜잭션을 붙이지 않는다. 매체 하나의 DB 실패가 앞서 성공한 다른 매체
 * 저장을 롤백하지 않도록 실제 저장은 별도 빈인 {@link VisionSummaryItemWriter}의
 * REQUIRES_NEW 트랜잭션에서 실행한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionSummaryIngestService {

    private final MediaUnitRepository mediaUnitRepository;
    private final CampaignRepository campaignRepository;
    private final VisionSummaryItemWriter itemWriter;

    /**
     * 매체별 결과를 끝까지 집계한다. 캠페인 없음과 기간 중복은 재시도로 해결되지 않으므로 해당
     * 매체만 건너뛰고, DB 접근 오류는 재시도 대상으로 기록한 뒤 다음 매체 처리를 계속한다.
     *
     * @throws MediaUnitNotFoundException board/device 코드에 해당하는 ACTIVE 매체가 하나도 없는 경우
     */
    public VisionFanOutResult ingest(VisionSummaryMessage message, String rawBody) {
        List<MediaUnit> mediaUnits =
                mediaUnitRepository.findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
                        message.boardId(), message.deviceId(), MediaUnitStatus.ACTIVE
                );
        if (mediaUnits.isEmpty()) {
            throw new MediaUnitNotFoundException(message.boardId(), message.deviceId());
        }

        LocalDateTime eventTimeUtc = LocalDateTime.ofInstant(message.timestamp(), ZoneOffset.UTC);
        LocalDate eventDateKst = KstDateTimes.todayKst(eventTimeUtc);

        int savedCount = 0;
        int duplicateCount = 0;
        int noCampaignCount = 0;
        int ambiguousCampaignCount = 0;
        int retryableFailureCount = 0;

        for (MediaUnit mediaUnit : mediaUnits) {
            List<Campaign> campaigns = campaignRepository.findActiveCampaignsForMediaUnit(
                    mediaUnit.getId(), eventDateKst, CampaignStatus.REGISTRATION_FAILED
            );
            if (campaigns.isEmpty()) {
                noCampaignCount++;
                continue;
            }
            if (campaigns.size() > 1) {
                ambiguousCampaignCount++;
                log.error(
                        "Vision campaign mapping ambiguous: mediaUnitId={}, eventDateKst={}, campaignIds={}",
                        mediaUnit.getId(),
                        eventDateKst,
                        campaigns.stream().map(Campaign::getId).toList()
                );
                continue;
            }

            try {
                itemWriter.write(message, rawBody, mediaUnit, campaigns.get(0), eventTimeUtc);
                savedCount++;
            } catch (DataIntegrityViolationException exception) {
                if (isDuplicateVisionSummary(exception)) {
                    duplicateCount++;
                    continue;
                }
                retryableFailureCount++;
                logRetryableFailure(message, mediaUnit, eventTimeUtc, exception);
            } catch (DataAccessException exception) {
                retryableFailureCount++;
                logRetryableFailure(message, mediaUnit, eventTimeUtc, exception);
            }
        }

        return new VisionFanOutResult(
                savedCount,
                duplicateCount,
                noCampaignCount,
                ambiguousCampaignCount,
                retryableFailureCount
        );
    }

    /**
     * H2와 MySQL이 유니크 제약 이름을 서로 다른 형태로 노출하므로 둘 다 확인한다.
     * 다른 무결성 오류는 잘못된 중복으로 숨기지 않고 재시도 실패로 분류한다.
     */
    private boolean isDuplicateVisionSummary(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String detail = cause == null ? exception.getMessage() : cause.getMessage();
        return detail != null && (
                detail.contains("uk_vision_summary_media_time")
                        || detail.contains("UK_VISION_SUMMARY_MEDIA_TIME")
                        || detail.contains("Unique index or primary key violation")
        );
    }

    private void logRetryableFailure(
            VisionSummaryMessage message,
            MediaUnit mediaUnit,
            LocalDateTime eventTimeUtc,
            DataAccessException exception
    ) {
        log.error(
                "Vision summary media transaction failed: mediaUnitId={}, device={}, board={}, seq={}, eventTime={}",
                mediaUnit.getId(),
                message.deviceId(),
                message.boardId(),
                message.seq(),
                eventTimeUtc,
                exception
        );
    }

    public static class MediaUnitNotFoundException extends RuntimeException {
        public MediaUnitNotFoundException(String boardId, String deviceId) {
            super("ACTIVE 매체를 찾을 수 없음: board_id=%s, device_id=%s".formatted(boardId, deviceId));
        }
    }
}
