package com.shinhan.klljs.domain.vision.service;

/**
 * Vision 메시지 한 건을 공용 장비에 연결된 모든 매체로 펼친 처리 결과다.
 *
 * <p>각 숫자는 서로 배타적인 매체별 최종 결과다. 재시도 가능한 실패가 한 건이라도 있으면 이미
 * 저장된 다른 매체 행은 유지하되 SQS 메시지는 ACK하지 않는다.</p>
 */
public record VisionFanOutResult(
        int savedCount,
        int duplicateCount,
        int noCampaignCount,
        int ambiguousCampaignCount,
        int retryableFailureCount
) {

    /** SQS DeleteMessage 호출 가능 여부다. 영구 건너뛰기 결과는 ACK을 막지 않는다. */
    public boolean shouldAck() {
        return retryableFailureCount == 0;
    }

    /** 공용 장비 코드에 매핑되어 이번 fan-out에서 확인한 ACTIVE 매체 수다. */
    public int processedMediaCount() {
        return savedCount
                + duplicateCount
                + noCampaignCount
                + ambiguousCampaignCount
                + retryableFailureCount;
    }
}
