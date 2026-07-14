package com.shinhan.klljs.domain.campaign.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * "오늘 하루 추정 송출 횟수" 계산 공식을 한곳에 모은다. DashboardCampaignDeliveryService의
 * 기간별 추정치 계산과 완전히 같은 전제(매일 06:00부터 15초 간격, 다운타임 없음)를 쓰므로,
 * 그 상수를 여기서 가져다 쓰게 해서 두 곳이 몰래 다른 값으로 어긋나는 걸 막는다.
 */
public final class CampaignPlayCountEstimator {

    public static final LocalTime PLAY_START_TIME = LocalTime.of(6, 0, 0);
    public static final int PLAY_INTERVAL_SEC = 15;

    private CampaignPlayCountEstimator() {
    }

    /**
     * 오늘(today) 하루치 추정 송출 횟수.
     * - 집행 시작 전이면 0.
     * - 집행 기간이 이미 끝났으면(과거 날짜는 항상 목표를 100% 채웠다고 가정) dailyTargetPlayCount.
     * - 집행 중이면 06:00부터 지금(nowKst)까지 경과 시간을 15초 간격으로 나눈 값(하루 목표치가 상한).
     */
    public static int estimateTodayPlayCount(
            LocalDate executionStartDate, LocalDate executionEndDate, int dailyTargetPlayCount,
            LocalDate today, LocalDateTime nowKst
    ) {
        if (today.isBefore(executionStartDate)) {
            return 0;
        }
        if (today.isAfter(executionEndDate)) {
            return dailyTargetPlayCount;
        }

        LocalDateTime todayStartKst = today.atTime(PLAY_START_TIME);
        long elapsedSec = Math.max(0, Duration.between(todayStartKst, nowKst).getSeconds());
        long todayRawEstimate = elapsedSec / PLAY_INTERVAL_SEC;
        return (int) Math.min(todayRawEstimate, dailyTargetPlayCount);
    }
}
