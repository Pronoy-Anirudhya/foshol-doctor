package com.rootcause.foshol.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class WorkingHoursCalendarTest {

    private final WorkingHoursCalendar calendar = new WorkingHoursCalendar(
            ZoneId.of("Asia/Dhaka"),
            LocalTime.of(10, 0),
            LocalTime.of(17, 0),
            EnumSet.of(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY));

    @Test
    void thursdayEveningWrapsToSunday() {
        Instant from = ZonedDateTime.of(2026, 1, 8, 16, 30, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant();
        Instant due = calendar.addWorking(from, Duration.ofHours(1));
        assertThat(due)
                .isEqualTo(ZonedDateTime.of(2026, 1, 11, 10, 30, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant());
    }

    @Test
    void fridaySnapsToSundayOpen() {
        Instant from = ZonedDateTime.of(2026, 1, 9, 16, 0, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant();
        Instant due = calendar.addWorking(from, Duration.ofHours(1));
        assertThat(due)
                .isEqualTo(ZonedDateTime.of(2026, 1, 11, 11, 0, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant());
    }

    @Test
    void fullWorkingDayFitsSameWindow() {
        Instant from = ZonedDateTime.of(2026, 1, 11, 10, 0, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant();
        Instant due = calendar.addWorking(from, Duration.ofHours(2));
        assertThat(due)
                .isEqualTo(ZonedDateTime.of(2026, 1, 11, 12, 0, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant());
    }
}
