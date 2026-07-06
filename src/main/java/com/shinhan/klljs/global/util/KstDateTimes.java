package com.shinhan.klljs.global.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public record UtcRange(LocalDateTime startUtc, LocalDateTime endUtc) {
    }
}
