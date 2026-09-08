package com.rootcause.foshol.review.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

public final class WorkingHoursCalendar {

    private final ZoneId zone;
    private final LocalTime workStart;
    private final LocalTime workEnd;
    private final Set<DayOfWeek> workDays;

    public WorkingHoursCalendar(ZoneId zone, LocalTime workStart, LocalTime workEnd, Set<DayOfWeek> workDays) {
        if (workStart == null || workEnd == null || !workStart.isBefore(workEnd)) {
            throw new IllegalArgumentException("work-start must be before work-end");
        }
        if (workDays == null || workDays.isEmpty()) {
            throw new IllegalArgumentException("work-days must not be empty");
        }
        this.zone = zone;
        this.workStart = workStart;
        this.workEnd = workEnd;
        this.workDays = EnumSet.copyOf(workDays);
    }

    public Instant addWorking(Instant from, Duration duration) {
        if (from == null) {
            throw new IllegalArgumentException("from is required");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return from;
        }
        ZonedDateTime cursor = snapToOpen(from.atZone(zone));
        Duration remaining = duration;
        int guard = 0;
        while (!remaining.isNegative() && !remaining.isZero()) {
            if (guard++ > 20_000) {
                throw new IllegalStateException("working-hours calendar did not terminate");
            }
            if (!isWorkDay(cursor.getDayOfWeek()) || !cursor.toLocalTime().isBefore(workEnd)) {
                cursor = nextOpen(cursor);
                continue;
            }
            ZonedDateTime windowEnd = cursor.toLocalDate().atTime(workEnd).atZone(zone);
            Duration available = Duration.between(cursor, windowEnd);
            if (remaining.compareTo(available) <= 0) {
                return cursor.plus(remaining).toInstant();
            }
            remaining = remaining.minus(available);
            cursor = nextOpen(cursor);
        }
        return cursor.toInstant();
    }

    public Instant warnAt(Instant dueAt, Duration warnBefore) {
        if (dueAt == null || warnBefore == null || warnBefore.isZero() || warnBefore.isNegative()) {
            return dueAt;
        }
        return dueAt.minus(warnBefore);
    }

    private ZonedDateTime snapToOpen(ZonedDateTime t) {
        if (isWorkDay(t.getDayOfWeek())) {
            LocalTime lt = t.toLocalTime();
            if (!lt.isBefore(workStart) && lt.isBefore(workEnd)) {
                return t;
            }
            if (lt.isBefore(workStart)) {
                return t.toLocalDate().atTime(workStart).atZone(zone);
            }
        }
        return nextOpen(t);
    }

    private ZonedDateTime nextOpen(ZonedDateTime t) {
        ZonedDateTime d = t.toLocalDate().plusDays(1).atTime(workStart).atZone(zone);
        while (!isWorkDay(d.getDayOfWeek())) {
            d = d.plusDays(1);
        }
        return d;
    }

    private boolean isWorkDay(DayOfWeek day) {
        return workDays.contains(day);
    }
}
