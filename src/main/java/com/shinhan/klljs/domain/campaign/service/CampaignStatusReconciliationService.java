package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 집행 시작일과 종료일을 KST 달력 날짜로 비교해 캠페인 상태를 실제 날짜와 맞춘다. */
@Service
@RequiredArgsConstructor
public class CampaignStatusReconciliationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CampaignRepository campaignRepository;
    private final Clock clock;

    /**
     * 등록 실패를 제외한 캠페인을 한 트랜잭션에서 보정한다.
     *
     * <p>상태가 이미 원하는 값인 엔티티는 변경하지 않아 불필요한 UPDATE를 피한다. 반환값은 실제로
     * 상태가 바뀐 캠페인 수이며 운영 로그와 테스트에서 처리 결과를 확인하는 데 사용한다.</p>
     */
    @Transactional
    public int reconcile() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        List<Campaign> campaigns = campaignRepository.findAllByStatusNot(CampaignStatus.REGISTRATION_FAILED);

        int changedCount = 0;
        for (Campaign campaign : campaigns) {
            CampaignStatus expectedStatus = statusFor(
                    campaign.getExecutionStartDate(), campaign.getExecutionEndDate(), today
            );
            if (campaign.getStatus() != expectedStatus) {
                campaign.changeStatus(expectedStatus);
                changedCount++;
            }
        }
        return changedCount;
    }

    /** 양 끝 날짜를 모두 집행 중으로 포함한다. */
    static CampaignStatus statusFor(LocalDate executionStartDate, LocalDate executionEndDate, LocalDate today) {
        if (today.isBefore(executionStartDate)) {
            return CampaignStatus.BEFORE_EXECUTION;
        }
        if (today.isAfter(executionEndDate)) {
            return CampaignStatus.AFTER_EXECUTION;
        }
        return CampaignStatus.IN_EXECUTION;
    }
}
