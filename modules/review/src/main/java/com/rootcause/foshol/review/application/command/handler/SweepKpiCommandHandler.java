package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.enums.KpiKind;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.KpiBreached;
import com.rootcause.foshol.common.events.KpiWarningIssued;
import com.rootcause.foshol.review.application.command.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.port.KpiBreachPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.KpiBreach;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SweepKpiCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SweepKpiCommandHandler.class);

    private final ReviewTaskRepository tasks;
    private final KpiBreachPort breaches;
    private final ReviewQueryPort reads;
    private final ReviewKpiCalendar kpi;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SweepKpiCommandHandler(
            ReviewTaskRepository tasks,
            KpiBreachPort breaches,
            ReviewQueryPort reads,
            ReviewKpiCalendar kpi,
            ApplicationEventPublisher events,
            Clock clock) {
        this.tasks = tasks;
        this.breaches = breaches;
        this.reads = reads;
        this.kpi = kpi;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void handle() {
        Instant now = clock.instant();
        String correlationId = CorrelationId.currentOrCreate();
        for (ReviewTask task : tasks.lockOverdueAssignments(now)) {
            recordBreach(task, KpiKind.ASSIGNMENT, task.assignmentOpenedAt(), task.assignmentDueAt(), now, correlationId);
        }
        for (ReviewTask task : tasks.lockOverdueResolutions(now)) {
            recordBreach(task, KpiKind.RESOLUTION, task.claimedAt(), task.resolutionDueAt(), now, correlationId);
        }
        Instant warnCutoff = now.plus(kpi.warnBefore());
        for (ReviewTask task : tasks.lockResolutionWarnings(warnCutoff, now)) {
            if (task.officerId() == null || task.resolutionDueAt() == null) {
                continue;
            }
            task.markKpiWarned(now);
            tasks.save(task);
            events.publishEvent(new KpiWarningIssued(
                    task.caseId(),
                    task.id(),
                    task.officerId(),
                    task.resolutionDueAt(),
                    correlationId,
                    now));
            log.info("kpi warning issued caseId={} taskId={}", task.caseId(), task.id());
        }
    }

    private void recordBreach(
            ReviewTask task, KpiKind kind, Instant windowStartedAt, Instant dueAt, Instant now, String correlationId) {
        if (windowStartedAt == null || dueAt == null) {
            return;
        }
        String district = reads.findQueueRow(task.id()).map(r -> r.districtCode()).orElse("");
        UUID officerId = kind == KpiKind.RESOLUTION ? task.officerId() : null;
        boolean inserted = breaches.insertIfAbsent(new KpiBreach(
                Uuid7.create(),
                task.id(),
                task.caseId(),
                district,
                kind,
                officerId,
                windowStartedAt,
                dueAt,
                now));
        if (inserted) {
            events.publishEvent(new KpiBreached(
                    kind, task.caseId(), task.id(), district, officerId, dueAt, correlationId, now));
            log.warn("kpi {} breached caseId={} taskId={}", kind, task.caseId(), task.id());
        }
    }
}
