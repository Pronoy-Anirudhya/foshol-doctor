package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.common.events.ReviewTaskTransferred;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.application.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskResult;
import com.rootcause.foshol.review.application.command.TransferReviewTaskCommand;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferReviewTaskCommandHandler
        implements CommandHandler<TransferReviewTaskCommand, ClaimReviewTaskResult> {

    @Override
    public Class<TransferReviewTaskCommand> commandType() {
        return TransferReviewTaskCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final OfficerQueueProjectionPort queue;
    private final ReviewQueryPort reads;
    private final OfficerLookupApi officers;
    private final ReviewDistrictGuard districtGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration claimTtl;

    public TransferReviewTaskCommandHandler(
            ReviewTaskRepository tasks,
            OfficerQueueProjectionPort queue,
            ReviewQueryPort reads,
            OfficerLookupApi officers,
            ReviewDistrictGuard districtGuard,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.queue = queue;
        this.reads = reads;
        this.officers = officers;
        this.districtGuard = districtGuard;
        this.events = events;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    @Override
    public ClaimReviewTaskResult handle(TransferReviewTaskCommand command) {
        districtGuard.requireTaskInCallerDistrict(command.fromOfficerId(), command.taskId());
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        Instant now = clock.instant();
        OfficerView target = officers
                .findById(command.toOfficerId())
                .orElseThrow(ReviewException::transferTargetInvalid);
        OfficerView caller = officers
                .findById(command.fromOfficerId())
                .orElseThrow(ReviewException::taskNotFound);
        if (!target.active()
                || !Role.OFFICER.name().equals(target.role())
                || target.districtCode() == null
                || !target.districtCode().equals(caller.districtCode())) {
            throw ReviewException.transferTargetInvalid();
        }
        task.transfer(command.fromOfficerId(), command.toOfficerId(), now, claimTtl);
        tasks.save(task);
        queue.updateState(task.caseId(), task.state(), task.officerId(), now);
        queue.updateKpiClocks(task.caseId(), task.assignmentDueAt(), task.resolutionDueAt(), now);
        String district = reads.findQueueRow(task.id()).map(r -> r.districtCode()).orElse(caller.districtCode());
        events.publishEvent(new ReviewTaskTransferred(
                task.caseId(),
                task.id(),
                command.fromOfficerId(),
                command.toOfficerId(),
                district,
                CorrelationId.currentOrCreate(),
                now));
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
