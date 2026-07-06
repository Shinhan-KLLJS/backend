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
    void toUtc_isInverseOfToKst() {
        LocalDateTime utc = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        LocalDateTime roundTripped = KstDateTimes.toUtc(KstDateTimes.toKst(utc));

        assertThat(roundTripped).isEqualTo(utc);
    }
}
