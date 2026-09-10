package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.review.application.command.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SweepExpiredClaimsCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SweepExpiredClaimsCommandHandler.class);

    private final ReviewTaskRepository tasks;
    private final OfficerQueueProjectionPort queue;
    private final ReviewKpiCalendar kpi;
    private final Clock clock;
    private final Duration claimTtl;

    public SweepExpiredClaimsCommandHandler(
            ReviewTaskRepository tasks,
            OfficerQueueProjectionPort queue,
            ReviewKpiCalendar kpi,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.queue = queue;
        this.kpi = kpi;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    public int handle() {
        Instant now = clock.instant();
        int swept = 0;
        for (ReviewTask task : tasks.lockExpiredClaims(now.minus(claimTtl))) {
            Instant opened = now;
            if (task.sweepExpired(now, claimTtl, opened, kpi.assignmentDue(opened))) {
                tasks.save(task);
                queue.updateState(task.caseId(), task.state(), null, now);
                queue.updateKpiClocks(task.caseId(), task.assignmentDueAt(), task.resolutionDueAt(), now);
                log.warn("Swept expired claim caseId={} taskId={}", task.caseId(), task.id());
                swept++;
            }
        }
        return swept;
    }
}
