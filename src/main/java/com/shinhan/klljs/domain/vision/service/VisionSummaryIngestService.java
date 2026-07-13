package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * SQS에서 꺼낸 Vision Summary 메시지 한 건을 실제로 DB에 저장하는 로직 (스펙 5-1절 "권장 구현 로직" 4~6단계).
 *
 * 여기 메서드들에는 일부러 @Transactional을 안 붙인다. Spring Data JPA 리포지토리 메서드
 * (findById, save, saveAndFlush 등)는 그 자체로 이미 트랜잭션 경계를 갖고 있어서
 * (SimpleJpaRepository 구현체 자체가 @Transactional) 서비스 메서드에 추가로 걸 필요가 없고,
 * 오히려 여기 걸었다가 같은 빈 안에서 self-invocation으로 호출하면 AOP 프록시를 안 타서
 * 트랜잭션이 조용히 무시되는 문제가 생길 수 있다(RefreshTokenFamilyRevoker를 별도 빈으로
 * 뺀 것과 같은 이유). saveAndFlush()가 자체적으로 커밋까지 시도하므로 unique 제약 위반은
 * 이 메서드를 호출하는 시점에 바로 예외로 터진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionSummaryIngestService {

    private final MediaUnitRepository mediaUnitRepository;
    private final CampaignRepository campaignRepository;
    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    /**
     * @return true = 새로 저장함, false = (media_unit_id, event_time) 중복이라 이미 처리된 메시지로 보고 건너뜀
     * @throws MediaUnitNotFoundException board_id/device_id에 매칭되는 매체가 없음 - 재시도해도
     *         해결되지 않는 오류라 호출한 컨슈머가 SQS 메시지를 삭제하지 않고 DLQ로 넘어가게 둔다.
     */
    public boolean ingest(VisionSummaryMessage message, String rawBody) {
        MediaUnit mediaUnit = mediaUnitRepository.findFirstByBoardCodeAndDeviceCodeOrderByIdAsc(message.boardId(), message.deviceId())
                .orElseThrow(() -> new MediaUnitNotFoundException(message.boardId(), message.deviceId()));

        LocalDateTime eventTimeUtc = LocalDateTime.ofInstant(message.timestamp(), ZoneOffset.UTC);
        LocalDate eventDateKst = KstDateTimes.todayKst(eventTimeUtc);

        Campaign campaign = resolveCampaign(mediaUnit.getId(), eventDateKst);

        VisionSummary5s entity = mapToEntity(message, rawBody, mediaUnit, campaign, eventTimeUtc);

        try {
            visionSummary5sRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateVisionSummary(e)) {
                return false;
            }
            log.error("Vision summary save failed: mediaUnitId={}, device={}, board={}, seq={}, eventTime={}",
                    mediaUnit.getId(), message.deviceId(), message.boardId(), message.seq(), eventTimeUtc, e);
            throw e;
        }
    }

    private boolean isDuplicateVisionSummary(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause == null ? e.getMessage() : cause.getMessage();
        return message != null && (
                message.contains("uk_vision_summary_media_time")
                        || message.contains("UK_VISION_SUMMARY_MEDIA_TIME")
                        || message.contains("Unique index or primary key violation")
        );
    }

    /**
     * 매체 + 이벤트 날짜(KST)로 캠페인을 찾는다. 못 찾으면 campaign_id는 null로 저장된다
     * (해당 매체/시간대에 아직 확정된 캠페인이 없는 경우 - 오류가 아니라 정상적으로 있을 수 있는 상태).
     * 정상적인 상황이면 매체 하나에 같은 날짜로 겹치는 캠페인은 하나뿐이어야 하지만, 캠페인 확정
     * 시점의 겹침 검증이 아직 구현되어 있지 않아 방어적으로 여러 건이면 경고 로그만 남기고
     * 첫 번째 것을 사용한다.
     */
    private Campaign resolveCampaign(Long mediaUnitId, LocalDate eventDateKst) {
        List<Campaign> candidates = campaignRepository.findActiveCampaignsForMediaUnit(mediaUnitId, eventDateKst);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            log.warn("매체 {}, 날짜 {}에 겹치는 캠페인이 {}건 있음 - 첫 번째 캠페인(id={})을 사용",
                    mediaUnitId, eventDateKst, candidates.size(), candidates.get(0).getId());
        }
        return candidates.get(0);
    }

    private VisionSummary5s mapToEntity(
            VisionSummaryMessage message, String rawBody, MediaUnit mediaUnit, Campaign campaign, LocalDateTime eventTimeUtc
    ) {
        VisionSummaryMessage.GenderDemographics otsMale = message.otsDemographics().male();
        VisionSummaryMessage.GenderDemographics otsFemale = message.otsDemographics().female();
        VisionSummaryMessage.GenderDemographics ltsMale = message.ltsDemographics().male();
        VisionSummaryMessage.GenderDemographics ltsFemale = message.ltsDemographics().female();
        VisionSummaryMessage.Attention attention = message.attention();
        VisionSummaryMessage.DwellDistribution dwell = attention.dwellDistribution();

        return VisionSummary5s.builder()
                .mediaUnit(mediaUnit)
                .campaign(campaign)
                .deviceId(message.deviceId())
                .boardId(message.boardId())
                .seq(message.seq())
                .eventTime(eventTimeUtc)
                .intervalSec(message.intervalSec())
                .receivedAt(LocalDateTime.now(clock))
                .rawPayload(rawBody)
                .otsCount(message.otsCount())
                .ltsCount(message.ltsCount())
                .otsMaleCount(otsMale.count())
                .otsFemaleCount(otsFemale.count())
                .ltsMaleCount(ltsMale.count())
                .ltsFemaleCount(ltsFemale.count())
                .otsMaleUnder10(otsMale.age().under10())
                .otsMale10s(otsMale.age().age10s())
                .otsMale20s(otsMale.age().age20s())
                .otsMale30s(otsMale.age().age30s())
                .otsMale40s(otsMale.age().age40s())
                .otsMale50s(otsMale.age().age50s())
                .otsMale60plus(otsMale.age().age60plus())
                .otsFemaleUnder10(otsFemale.age().under10())
                .otsFemale10s(otsFemale.age().age10s())
                .otsFemale20s(otsFemale.age().age20s())
                .otsFemale30s(otsFemale.age().age30s())
                .otsFemale40s(otsFemale.age().age40s())
                .otsFemale50s(otsFemale.age().age50s())
                .otsFemale60plus(otsFemale.age().age60plus())
                .ltsMaleUnder10(ltsMale.age().under10())
                .ltsMale10s(ltsMale.age().age10s())
                .ltsMale20s(ltsMale.age().age20s())
                .ltsMale30s(ltsMale.age().age30s())
                .ltsMale40s(ltsMale.age().age40s())
                .ltsMale50s(ltsMale.age().age50s())
                .ltsMale60plus(ltsMale.age().age60plus())
                .ltsFemaleUnder10(ltsFemale.age().under10())
                .ltsFemale10s(ltsFemale.age().age10s())
                .ltsFemale20s(ltsFemale.age().age20s())
                .ltsFemale30s(ltsFemale.age().age30s())
                .ltsFemale40s(ltsFemale.age().age40s())
                .ltsFemale50s(ltsFemale.age().age50s())
                .ltsFemale60plus(ltsFemale.age().age60plus())
                .avgDwellSec(attention.avgDwellSec())
                .dwellSumSec(attention.dwellSumSec())
                .dwell1ToUnder2s(dwell.oneToUnder2s())
                .dwell2ToUnder3s(dwell.twoToUnder3s())
                .dwell3ToUnder4s(dwell.threeToUnder4s())
                .dwell4sAndOver(dwell.fourSAndOver())
                .build();
    }

    public static class MediaUnitNotFoundException extends RuntimeException {
        public MediaUnitNotFoundException(String boardId, String deviceId) {
            super("매체를 찾을 수 없음: board_id=%s, device_id=%s".formatted(boardId, deviceId));
        }
    }
}
