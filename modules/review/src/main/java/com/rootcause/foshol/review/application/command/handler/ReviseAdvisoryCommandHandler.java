package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.AdvisoryRevised;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.AdvisoryViewMapper;
import com.rootcause.foshol.review.application.command.ApproveCaseResult;
import com.rootcause.foshol.review.application.command.ReviseAdvisoryCommand;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviseAdvisoryCommandHandler implements CommandHandler<ReviseAdvisoryCommand, ApproveCaseResult> {

    @Override
    public Class<ReviseAdvisoryCommand> commandType() {
        return ReviseAdvisoryCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final AdvisoryRepository advisories;
    private final OfficerQueueProjectionPort queue;
    private final OfficerLookupApi officers;
    private final CaseIntakeApi cases;
    private final KnowledgeQueryApi knowledge;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ApproveCaseCommandHandler approve;

    public ReviseAdvisoryCommandHandler(
            ReviewTaskRepository tasks,
            AdvisoryRepository advisories,
            OfficerQueueProjectionPort queue,
            KnowledgeQueryApi knowledge,
            OfficerLookupApi officers,
            CaseIntakeApi cases,
            ApplicationEventPublisher events,
            Clock clock,
            ApproveCaseCommandHandler approve) {
        this.tasks = tasks;
        this.advisories = advisories;
        this.queue = queue;
        this.officers = officers;
        this.cases = cases;
        this.knowledge = knowledge;
        this.events = events;
        this.clock = clock;
        this.approve = approve;
    }

    @Transactional
    @Override
    public ApproveCaseResult handle(ReviseAdvisoryCommand command) {
        Advisory current = advisories.findById(command.advisoryId()).orElseThrow(ReviewException::advisoryNotFound);
        Advisory published = advisories
                .findPublishedByCaseId(current.caseId())
                .orElseThrow(ReviewException::advisoryNotFound);
        if (!published.id().equals(current.id())) {
            throw ReviewException.advisoryNotFound();
        }
        ReviewTask task = tasks.findByCaseId(current.caseId()).orElseThrow(ReviewException::taskNotFound);
        Instant now = clock.instant();
        task.reclaimForRevision(command.officerId(), now);
        try {
            tasks.save(task);
        } catch (OptimisticLockingFailureException ex) {
            throw ReviewException.claimConflict();
        }
        Advisory draft = approve.buildAdvisory(
                Uuid7.create(),
                current.caseId(),
                command.officerId(),
                command.diseaseId(),
                command.remedyIds(),
                command.officerNoteBn(),
                now,
                null);
        Advisory next = current.revise(
                Uuid7.create(),
                draft.diseaseId(),
                command.officerId(),
                draft.action(),
                command.officerNoteBn(),
                draft.remedies(),
                now);
        advisories.insert(next);
        task.markDone(now, command.officerId().toString());
        tasks.save(task);
        queue.updateState(task.caseId(), task.state(), task.officerId(), now);
        UUID farmerId = cases.findById(task.caseId()).map(c -> c.farmerId()).orElse(task.caseId());
        String officerName = officers.findById(command.officerId()).map(o -> o.name()).orElse("");
        events.publishEvent(new AdvisoryRevised(
                next.id(),
                current.id(),
                next.caseId(),
                farmerId,
                command.officerId(),
                officerName,
                next.version(),
                CorrelationId.currentOrCreate(),
                now));
        return new ApproveCaseResult(AdvisoryViewMapper.toView(next, knowledge, officers));
    }
}
