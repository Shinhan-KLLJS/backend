package com.shinhan.klljs.global.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * DB/애플리케이션 내부의 모든 시각(예: vision_summary_5s.event_time, *.created_at)은 UTC로 저장·처리한다.
 * "오늘", "이번 주", "시간대별" 같은 업무 경계만 KST(Asia/Seoul, UTC+9, DST 없음) 기준으로 계산해서
 * UTC LocalDateTime으로 변환한 뒤 그 값으로 조회한다. 한국은 서머타임이 없어 고정 오프셋(+9)만으로 충분하다.
 */
public final class KstDateTimes {

    public static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private KstDateTimes() {
    }

    /** 지금(UTC) 이 순간의 KST 날짜. nowUtc는 반드시 UTC를 나타내는 값이어야 한다 (예: LocalDateTime.now(ZoneOffset.UTC)). */
    public static LocalDate todayKst(LocalDateTime nowUtc) {
        return toKst(nowUtc).toLocalDate();
    }

    /** UTC LocalDateTime -> KST LocalDateTime (표시, 시간대 그룹핑 등에 사용) */
    public static LocalDateTime toKst(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(KST)
                .toLocalDateTime();
    }

    /**
     * UTC LocalDateTime -> KST OffsetDateTime. API 응답의 serverTime/aggregationCutoffTime/
     * lastEventTime/nextPollAfter 등, 스펙에서 "+09:00"이 붙은 KST ISO-8601 문자열로
     * 내려주기로 정한 필드에 쓴다 (예: "2026-07-07T16:43:25+09:00"). Jackson jsr310 모듈이
     * OffsetDateTime을 기본적으로 이 형식 그대로 직렬화해주므로 별도 포맷팅이 필요 없다.
     * toKst()가 반환하는 LocalDateTime은 오프셋 정보가 없어 그대로 직렬화하면 "+09:00"이
     * 안 붙으므로, 응답 필드에는 toKst() 대신 이 메서드를 쓴다.
     */
    public static OffsetDateTime toKstOffset(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }

    /** KST LocalDateTime -> UTC LocalDateTime (DB 조회 조건에 사용) */
    public static LocalDateTime toUtc(LocalDateTime kst) {
        return kst.atOffset(KST)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /** 주어진 KST 날짜의 [00:00, 다음날 00:00) 구간을 UTC LocalDateTime으로 변환 */
    public static UtcRange kstDayRangeUtc(LocalDate kstDate) {
        LocalDateTime startUtc = toUtc(kstDate.atStartOfDay());
        LocalDateTime endUtc = toUtc(kstDate.plusDays(1).atStartOfDay());
        return new UtcRange(startUtc, endUtc);
    }

    /**
     * kstDayRangeUtc()의 여러 날짜 버전. [startDate, endDateInclusive](둘 다 KST 달력 날짜,
     * 양끝 포함) 구간 전체를 [시작일 00:00, 종료일 다음날 00:00) UTC 범위로 변환한다.
     * 캠페인 집행/선택 기간처럼 "시작일~종료일"로 표현되는 기간을 그대로 DB 조회 범위로
     * 바꿀 때 쓴다 (예: 5-2절 시간별 누적 그래프처럼 하루를 넘는 기간을 한 번에 조회하는 API).
     */
    public static UtcRange kstRangeUtc(LocalDate startDate, LocalDate endDateInclusive) {
        LocalDateTime startUtc = toUtc(startDate.atStartOfDay());
        LocalDateTime endUtc = toUtc(endDateInclusive.plusDays(1).atStartOfDay());
        return new UtcRange(startUtc, endUtc);
    }

    public record UtcRange(LocalDateTime startUtc, LocalDateTime endUtc) {
    }
}
