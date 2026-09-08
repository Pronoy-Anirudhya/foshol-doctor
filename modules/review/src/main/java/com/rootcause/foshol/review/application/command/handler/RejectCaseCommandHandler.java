package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.application.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.CaseRejectionRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.CaseRejection;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RejectCaseCommandHandler implements CommandHandler<RejectCaseCommand, RejectionView> {

    @Override
    public Class<RejectCaseCommand> commandType() {
        return RejectCaseCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final CaseRejectionRepository rejections;
    private final AdvisoryRepository advisories;
    private final OfficerQueueProjectionPort queue;
    private final OfficerLookupApi officers;
    private final CaseIntakeApi cases;
    private final ReviewDistrictGuard districtGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration claimTtl;

    public RejectCaseCommandHandler(
            ReviewTaskRepository tasks,
            CaseRejectionRepository rejections,
            AdvisoryRepository advisories,
            OfficerQueueProjectionPort queue,
            OfficerLookupApi officers,
            CaseIntakeApi cases,
            ReviewDistrictGuard districtGuard,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.rejections = rejections;
        this.advisories = advisories;
        this.queue = queue;
        this.officers = officers;
        this.cases = cases;
        this.districtGuard = districtGuard;
        this.events = events;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    @Override
    public RejectionView handle(RejectCaseCommand command) {
        districtGuard.requireTaskInCallerDistrict(command.officerId(), command.taskId());
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        Instant now = clock.instant();
        task.requireLiveClaim(command.officerId(), now, claimTtl);
        if (advisories.findPublishedByCaseId(task.caseId()).isPresent()) {
            throw ReviewException.terminal();
        }
        CaseRejection rejection = new CaseRejection(
                Uuid7.create(),
                task.caseId(),
                command.officerId(),
                command.reasonCode(),
                command.messageBn(),
                now);
        rejections.insert(rejection);
        task.markRejected(now, command.officerId().toString());
        tasks.save(task);
        queue.updateState(task.caseId(), task.state(), task.officerId(), now);
        UUID farmerId = cases.findById(task.caseId()).map(c -> c.farmerId()).orElse(task.caseId());
        String officerName = officers.findById(command.officerId()).map(o -> o.name()).orElse("");
        events.publishEvent(new CaseRejected(
                task.caseId(),
                farmerId,
                command.officerId(),
                officerName,
                rejection.reasonCode(),
                rejection.messageBn(),
                CorrelationId.currentOrCreate(),
                now));
        log.info("case rejected caseId={} taskId={} reason={}", task.caseId(), task.id(), command.reasonCode());
        return new RejectionView(
                rejection.caseId(),
                rejection.officerId(),
                officerName,
                rejection.reasonCode(),
                rejection.messageBn(),
                rejection.createdAt());
    }
}
