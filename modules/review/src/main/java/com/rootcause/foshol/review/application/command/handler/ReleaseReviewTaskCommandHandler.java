package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.review.application.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskResult;
import com.rootcause.foshol.review.application.command.ReleaseReviewTaskCommand;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReleaseReviewTaskCommandHandler implements CommandHandler<ReleaseReviewTaskCommand, ClaimReviewTaskResult> {

    @Override
    public Class<ReleaseReviewTaskCommand> commandType() {
        return ReleaseReviewTaskCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final OfficerQueueProjectionPort queue;
    private final ReviewDistrictGuard districtGuard;
    private final ReviewKpiCalendar kpi;
    private final Clock clock;
    private final Duration claimTtl;

    public ReleaseReviewTaskCommandHandler(
            ReviewTaskRepository tasks,
            OfficerQueueProjectionPort queue,
            ReviewDistrictGuard districtGuard,
            ReviewKpiCalendar kpi,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.queue = queue;
        this.districtGuard = districtGuard;
        this.kpi = kpi;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    @Override
    public ClaimReviewTaskResult handle(ReleaseReviewTaskCommand command) {
        districtGuard.requireTaskInCallerDistrict(command.officerId(), command.taskId());
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        Instant now = clock.instant();
        task.release(command.officerId(), now, claimTtl, now, kpi.assignmentDue(now));
        tasks.save(task);
        queue.updateState(task.caseId(), task.state(), task.officerId(), now);
        queue.updateKpiClocks(task.caseId(), task.assignmentDueAt(), task.resolutionDueAt(), now);
        log.info("review task released taskId={} caseId={}", task.id(), task.caseId());
        return new ClaimReviewTaskResult(
                task.id(),
                task.caseId(),
                task.state(),
                task.officerId(),
                task.claimedAt(),
                task.claimExpiresAt(claimTtl),
                task.slaDueAt(),
                task.assignmentDueAt(),
                task.resolutionDueAt(),
                task.requeueCount(),
                task.version());
    }
}
