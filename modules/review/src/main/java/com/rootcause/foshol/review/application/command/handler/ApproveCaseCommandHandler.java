package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.application.command.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.query.AdvisoryViewMapper;
import com.rootcause.foshol.review.application.command.AdvisoryActionDeriver;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.ApproveCaseResult;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.review.domain.spec.AdvisoryHasRequiredRemedy;
import com.rootcause.foshol.review.domain.spec.ChemicalRemedyHasPhi;
import com.rootcause.foshol.review.domain.spec.RemediesBelongToDisease;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ApproveCaseCommandHandler implements CommandHandler<ApproveCaseCommand, ApproveCaseResult> {

    @Override
    public Class<ApproveCaseCommand> commandType() {
        return ApproveCaseCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final AdvisoryRepository advisories;
    private final OfficerQueueProjectionPort queue;
    private final AnalysisApi analysisApi;
    private final KnowledgeQueryApi knowledge;
    private final OfficerLookupApi officers;
    private final CaseIntakeApi cases;
    private final ReviewDistrictGuard districtGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration claimTtl;

    public ApproveCaseCommandHandler(
            ReviewTaskRepository tasks,
            AdvisoryRepository advisories,
            OfficerQueueProjectionPort queue,
            AnalysisApi analysisApi,
            KnowledgeQueryApi knowledge,
            OfficerLookupApi officers,
            CaseIntakeApi cases,
            ReviewDistrictGuard districtGuard,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.advisories = advisories;
        this.queue = queue;
        this.analysisApi = analysisApi;
        this.knowledge = knowledge;
        this.officers = officers;
        this.cases = cases;
        this.districtGuard = districtGuard;
        this.events = events;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    @Override
    public ApproveCaseResult handle(ApproveCaseCommand command) {
        districtGuard.requireTaskInCallerDistrict(command.officerId(), command.taskId());
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        Instant now = clock.instant();
        task.requireLiveClaim(command.officerId(), now, claimTtl);
        Advisory advisory = buildAdvisory(
                Uuid7.create(),
                task.caseId(),
                command.officerId(),
                command.diseaseId(),
                command.remedyIds(),
                command.officerNoteBn(),
                now,
                null);
        advisories.insert(advisory);
        task.markDone(now, command.officerId().toString());
        tasks.save(task);
        queue.updateState(task.caseId(), task.state(), task.officerId(), now);
        UUID farmerId = cases.findById(task.caseId()).map(c -> c.farmerId()).orElse(task.caseId());
        String officerName = officers.findById(command.officerId()).map(o -> o.name()).orElse("");
        String diseaseName = knowledge.findDiseaseById(advisory.diseaseId()).map(DiseaseView::nameBn).orElse("");
        events.publishEvent(new AdvisoryApproved(
                advisory.id(),
                advisory.caseId(),
                farmerId,
                command.officerId(),
                officerName,
                advisory.diseaseId(),
                diseaseName,
                advisory.action(),
                advisory.version(),
                CorrelationId.currentOrCreate(),
                now));
        log.info("advisory approved caseId={} taskId={} advisoryId={}", task.caseId(), task.id(), advisory.id());
        return new ApproveCaseResult(AdvisoryViewMapper.toView(advisory, knowledge, officers));
    }

    Advisory buildAdvisory(
            UUID id,
            UUID caseId,
            UUID officerId,
            UUID diseaseId,
            List<UUID> remedyIds,
            String officerNoteBn,
            Instant now,
            UUID supersedesId) {
        List<UUID> ids = remedyIds == null ? List.of() : remedyIds;
        DiseaseView disease = knowledge.findDiseaseById(diseaseId).orElseThrow(ReviewException::remedyDiseaseMismatch);
        List<RemedyView> kb = knowledge.listActiveRemedies(diseaseId);
        if (!new AdvisoryHasRequiredRemedy(disease).isSatisfiedBy(ids)) {
            throw ReviewException.requiresRemedy();
        }
        List<AdvisoryRemedy> selected = new ArrayList<>();
        short order = 0;
        for (UUID remedyId : ids) {
            selected.add(new AdvisoryRemedy(remedyId, order++));
        }
        if (!new RemediesBelongToDisease(kb).isSatisfiedBy(selected)) {
            throw ReviewException.remedyDiseaseMismatch();
        }
        if (!new ChemicalRemedyHasPhi(kb).isSatisfiedBy(ids)) {
            throw ReviewException.remedyPhiMissing();
        }
        List<CandidateView> candidates = analysisApi
                .findByCaseId(caseId)
                .map(v -> v.candidates() == null ? List.<CandidateView>of() : v.candidates())
                .orElse(List.of());
        AdvisoryAction action = AdvisoryActionDeriver.derive(diseaseId, ids, officerNoteBn, candidates, kb);
        if (supersedesId == null) {
            return Advisory.firstVersion(id, caseId, diseaseId, officerId, action, officerNoteBn, selected, now);
        }
        return new Advisory(
                id,
                caseId,
                diseaseId,
                officerId,
                action,
                officerNoteBn,
                (short) 2,
                supersedesId,
                selected,
                now,
                now,
                officerId.toString());
    }
}
