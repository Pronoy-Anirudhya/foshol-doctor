package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimReviewTaskCommandHandler {

    private final ReviewTaskRepository tasks;
    private final OfficerQueueProjectionPort queue;
    private final Clock clock;
    private final Duration claimTtl;

    public ClaimReviewTaskCommandHandler(
            ReviewTaskRepository tasks,
            OfficerQueueProjectionPort queue,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.queue = queue;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    public ClaimReviewTaskResult handle(ClaimReviewTaskCommand command) {
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        task.claim(command.officerId(), clock.instant(), claimTtl);
        try {
            tasks.save(task);
        } catch (OptimisticLockingFailureException ex) {
            throw ReviewException.claimConflict();
        }
        queue.updateState(task.caseId(), task.state(), task.officerId(), clock.instant());
        return new ClaimReviewTaskResult(
                task.id(),
                task.caseId(),
                task.state(),
                task.officerId(),
                task.claimedAt(),
                task.claimExpiresAt(claimTtl),
                task.slaDueAt(),
                task.requeueCount(),
                task.version());
    }
}
