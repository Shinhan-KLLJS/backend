package com.shinhan.klljs.domain.campaign.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 캠페인 집행 상태를 KST 날짜에 맞게 보정하는 진입점이다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignStatusScheduler {

    private final CampaignStatusReconciliationService reconciliationService;

    /** 분 경계에 실행해 등록 직후의 REGISTERED 상태가 최대 1분 안에 집행 상태로 전환되게 한다. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void reconcileCampaignStatuses() {
        int changedCount = reconciliationService.reconcile();
        if (changedCount > 0) {
            log.info("캠페인 집행 상태 보정 완료: changedCount={}", changedCount);
        }
    }
}
