package com.shinhan.klljs.domain.vision.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;
import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 매체 하나의 Vision 행을 독립 트랜잭션으로 저장한다.
 *
 * <p>반드시 fan-out 오케스트레이터와 다른 Spring Bean이어야 REQUIRES_NEW 프록시가 적용된다.
 * saveAndFlush로 커밋 전에 유니크 제약 위반을 드러내 호출자가 중복과 다른 DB 오류를 구분하게 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class VisionSummaryItemWriter {

    private final VisionSummary5sRepository visionSummary5sRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(
            VisionSummaryMessage message,
            String rawBody,
            MediaUnit mediaUnit,
            Campaign campaign,
            LocalDateTime eventTimeUtc
    ) {
        VisionSummaryMessage.GenderDemographics otsMale = message.otsDemographics().male();
        VisionSummaryMessage.GenderDemographics otsFemale = message.otsDemographics().female();
        VisionSummaryMessage.GenderDemographics ltsMale = message.ltsDemographics().male();
        VisionSummaryMessage.GenderDemographics ltsFemale = message.ltsDemographics().female();
        VisionSummaryMessage.Attention attention = message.attention();
        VisionSummaryMessage.DwellDistribution dwell = attention.dwellDistribution();

        VisionSummary5s entity = VisionSummary5s.builder()
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

        visionSummary5sRepository.saveAndFlush(entity);
    }
}
