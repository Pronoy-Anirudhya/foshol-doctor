package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.review.domain.WorkingHoursCalendar;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewKpiCalendar {

    private final WorkingHoursCalendar hours;
    private final Duration assignmentSla;
    private final Duration resolutionSla;
    private final Duration warnBefore;

    public ReviewKpiCalendar(
            @Value("${" + ConfigKeys.REVIEW_KPI_ZONE + ":Asia/Dhaka}") String zone,
            @Value("${" + ConfigKeys.REVIEW_KPI_WORK_START + ":10:00}") String workStart,
            @Value("${" + ConfigKeys.REVIEW_KPI_WORK_END + ":17:00}") String workEnd,
            @Value("${" + ConfigKeys.REVIEW_KPI_WORK_DAYS + ":SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY}")
                    String workDays,
            @Value("${" + ConfigKeys.REVIEW_KPI_ASSIGNMENT_SLA + ":PT1H}") Duration assignmentSla,
            @Value("${" + ConfigKeys.REVIEW_KPI_RESOLUTION_SLA + ":PT2H}") Duration resolutionSla,
            @Value("${" + ConfigKeys.REVIEW_KPI_WARN_BEFORE + ":PT15M}") Duration warnBefore) {
        this.hours = new WorkingHoursCalendar(
                ZoneId.of(zone), LocalTime.parse(workStart), LocalTime.parse(workEnd), parseDays(workDays));
        this.assignmentSla = assignmentSla;
        this.resolutionSla = resolutionSla;
        this.warnBefore = warnBefore;
    }

    public static ReviewKpiCalendar alwaysOpenUtc() {
        return new ReviewKpiCalendar(
                "UTC",
                "00:00",
                "23:59",
                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                Duration.ofHours(1),
                Duration.ofHours(2),
                Duration.ofMinutes(15));
    }

    public Instant assignmentDue(Instant from) {
        return hours.addWorking(from, assignmentSla);
    }

    public Instant resolutionDue(Instant from) {
        return hours.addWorking(from, resolutionSla);
    }

    public Duration warnBefore() {
        return warnBefore;
    }

    public boolean inResolutionWarnWindow(Instant now, Instant resolutionDueAt) {
        if (now == null || resolutionDueAt == null) {
            return false;
        }
        Instant warnAt = hours.warnAt(resolutionDueAt, warnBefore);
        return !now.isBefore(warnAt) && now.isBefore(resolutionDueAt);
    }

    private static Set<DayOfWeek> parseDays(String raw) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                days.add(DayOfWeek.valueOf(trimmed.toUpperCase(Locale.ROOT)));
            }
        }
        return days;
    }
}
