package com.rootcause.foshol.review.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.KpiKind;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.KpiBreached;
import com.rootcause.foshol.common.events.KpiWarningIssued;
import com.rootcause.foshol.review.application.command.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.port.KpiBreachPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort.QueueTaskRow;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SweepKpiCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private KpiBreachPort breaches;

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void recordsAssignmentBreachOnce() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        ReviewTask pending = ReviewTask.createPending(
                Uuid7.create(),
                Uuid7.create(),
                null,
                now.plus(Duration.ofHours(4)),
                now.minus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(2)),
                now.minus(Duration.ofMinutes(1)));
        when(tasks.lockOverdueAssignments(now)).thenReturn(List.of(pending));
        when(tasks.lockOverdueResolutions(now)).thenReturn(List.of());
        when(tasks.lockResolutionWarnings(any(), any())).thenReturn(List.of());
        when(reads.findQueueRow(pending.id())).thenReturn(Optional.of(row(pending, "DHA")));
        when(breaches.insertIfAbsent(any())).thenReturn(true);
        SweepKpiCommandHandler handler = new SweepKpiCommandHandler(
                tasks,
                breaches,
                reads,
                ReviewKpiCalendar.alwaysOpenUtc(),
                events,
                Clock.fixed(now, ZoneOffset.UTC));
        handler.handle();
        ArgumentCaptor<KpiBreached> captor = ArgumentCaptor.forClass(KpiBreached.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(KpiKind.ASSIGNMENT);
        assertThat(captor.getValue().officerId()).isNull();
    }

    @Test
    void emitsWarningOnce() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, now.plus(Duration.ofHours(4)), now.minus(Duration.ofHours(1)));
        task.claim(Uuid7.create(), now.minus(Duration.ofMinutes(5)), Duration.ofHours(2), now.plus(Duration.ofMinutes(10)));
        when(tasks.lockOverdueAssignments(now)).thenReturn(List.of());
        when(tasks.lockOverdueResolutions(now)).thenReturn(List.of());
        when(tasks.lockResolutionWarnings(any(), any())).thenReturn(List.of(task));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SweepKpiCommandHandler handler = new SweepKpiCommandHandler(
                tasks,
                breaches,
                reads,
                ReviewKpiCalendar.alwaysOpenUtc(),
                events,
                Clock.fixed(now, ZoneOffset.UTC));
        handler.handle();
        verify(events).publishEvent(org.mockito.ArgumentMatchers.isA(KpiWarningIssued.class));
        assertThat(task.kpiWarnEmittedAt()).isEqualTo(now);
    }

    private static QueueTaskRow row(ReviewTask task, String district) {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        return new QueueTaskRow(
                task.caseId(),
                task.id(),
                "F",
                "rice",
                "ধান",
                district,
                "PRIMARY",
                Uuid7.create(),
                "d",
                new BigDecimal("0.50"),
                1,
                false,
                "REPLAY",
                "PENDING",
                null,
                false,
                (short) 0,
                t0,
                t0,
                t0,
                null);
    }
}
