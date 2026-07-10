package com.shinhan.klljs.global.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KstDateTimesTest {

    @Test
    void toKst_addsNineHours() {
        LocalDateTime utc = LocalDateTime.of(2026, 7, 8, 15, 30, 0);

        LocalDateTime kst = KstDateTimes.toKst(utc);

        assertThat(kst).isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 30, 0));
    }

    @Test
    void todayKst_crossesMidnightBeforeUtcDoes() {
        // UTC 07-08 15:30 = KST 07-09 00:30 → KST 기준으로는 이미 07-09
        LocalDateTime utcJustAfterKstMidnight = LocalDateTime.of(2026, 7, 8, 15, 30, 0);

        LocalDate kstDate = KstDateTimes.todayKst(utcJustAfterKstMidnight);

        assertThat(kstDate).isEqualTo(LocalDate.of(2026, 7, 9));
    }

    @Test
    void kstDayRangeUtc_returnsUtcBoundsForKstCalendarDay() {
        LocalDate kstDate = LocalDate.of(2026, 7, 9);

        KstDateTimes.UtcRange range = KstDateTimes.kstDayRangeUtc(kstDate);

        // KST 07-09 00:00 = UTC 07-08 15:00, KST 07-10 00:00 = UTC 07-09 15:00
        assertThat(range.startUtc()).isEqualTo(LocalDateTime.of(2026, 7, 8, 15, 0, 0));
        assertThat(range.endUtc()).isEqualTo(LocalDateTime.of(2026, 7, 9, 15, 0, 0));
    }

    @Test
    void kstRangeUtc_returnsUtcBoundsSpanningMultipleKstDays() {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDateInclusive = LocalDate.of(2026, 7, 5);

        KstDateTimes.UtcRange range = KstDateTimes.kstRangeUtc(startDate, endDateInclusive);

        // KST 07-01 00:00 = UTC 06-30 15:00, KST 07-06 00:00(종료일 다음날) = UTC 07-05 15:00
        assertThat(range.startUtc()).isEqualTo(LocalDateTime.of(2026, 6, 30, 15, 0, 0));
        assertThat(range.endUtc()).isEqualTo(LocalDateTime.of(2026, 7, 5, 15, 0, 0));
    }

    @Test
    void kstRangeUtc_withSameStartAndEndDateMatchesKstDayRangeUtc() {
        LocalDate onlyDate = LocalDate.of(2026, 7, 9);

        KstDateTimes.UtcRange rangeUtc = KstDateTimes.kstRangeUtc(onlyDate, onlyDate);
        KstDateTimes.UtcRange dayRangeUtc = KstDateTimes.kstDayRangeUtc(onlyDate);

        assertThat(rangeUtc).isEqualTo(dayRangeUtc);
    }

    @Test
    void toUtc_isInverseOfToKst() {
        LocalDateTime utc = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        LocalDateTime roundTripped = KstDateTimes.toUtc(KstDateTimes.toKst(utc));

        assertThat(roundTripped).isEqualTo(utc);
    }
}
