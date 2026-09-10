package com.rootcause.foshol.intake.infrastructure.listener;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommand;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CaseStatusListeners {

    private final CommandBus commands;
    private final DiagnosisCaseRepository cases;
    private final KnowledgeQueryApi knowledge;

    public CaseStatusListeners(CommandBus commands, DiagnosisCaseRepository cases, KnowledgeQueryApi knowledge) {
        this.commands = commands;
        this.cases = cases;
        this.knowledge = knowledge;
    }

    @ApplicationModuleListener
    public void onSubmitted(CaseSubmitted event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseSubmitted caseId={}", event.caseId());
        safeStatus(event.caseId(), CaseStatus.ANALYSING);
    }

    @ApplicationModuleListener
    public void onAnalysisCompleted(AnalysisCompleted event) {
        CorrelationId.set(event.correlationId());
        log.info("event AnalysisCompleted caseId={} path={}", event.caseId(), event.decisionPath());
        try {
            commands.handle(new RecordAnalysisOutcomeCommand(event.caseId(), event.decisionPath(), false));
        } catch (CaseNotFoundException ex) {
            log.warn("analysis completed for unknown caseId={}", event.caseId());
        }
        cases.findById(CaseId.of(event.caseId())).ifPresent(this::persistHistory);
    }

    @ApplicationModuleListener
    public void onAnalysisFailed(AnalysisFailed event) {
        CorrelationId.set(event.correlationId());
        log.info("event AnalysisFailed caseId={}", event.caseId());
        try {
            commands.handle(new RecordAnalysisOutcomeCommand(event.caseId(), null, true));
        } catch (CaseNotFoundException ex) {
            log.warn("analysis failed for unknown caseId={}", event.caseId());
        }
    }

    @ApplicationModuleListener
    public void onAdvisoryApproved(AdvisoryApproved event) {
        CorrelationId.set(event.correlationId());
        log.info("event AdvisoryApproved caseId={} advisoryId={}", event.caseId(), event.advisoryId());
        safeStatus(event.caseId(), CaseStatus.ADVISED);
        cases.enrichAdvisory(
                event.caseId(),
                event.advisoryId(),
                event.version(),
                event.diseaseNameBn(),
                event.officerName(),
                event.occurredAt());
    }

    @ApplicationModuleListener
    public void onCaseRejected(CaseRejected event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseRejected caseId={}", event.caseId());
        safeStatus(event.caseId(), CaseStatus.REJECTED);
        cases.enrichRejection(event.caseId(), event.messageBn());
    }

    @ApplicationModuleListener
    public void onStatusChanged(CaseStatusChanged event) {
        CorrelationId.set(event.correlationId());
        cases.findById(CaseId.of(event.caseId())).ifPresent(this::persistHistory);
    }

    private void safeStatus(java.util.UUID caseId, CaseStatus target) {
        try {
            commands.handle(new ChangeCaseStatusCommand(caseId, target));
        } catch (CaseNotFoundException ex) {
            log.warn("status event for unknown caseId={}", caseId);
        }
    }

    private void persistHistory(DiagnosisCase diagnosisCase) {
        String cropName = knowledge
                .findCropById(diagnosisCase.cropId())
                .map(c -> c.nameBn())
                .orElse("crop");
        String thumb = diagnosisCase.images().isEmpty()
                ? null
                : diagnosisCase.primaryImage().derivativeObjectKey() == null
                        ? diagnosisCase.primaryImage().objectKey().value()
                        : diagnosisCase.primaryImage().derivativeObjectKey().value();
        cases.upsertFarmerHistory(diagnosisCase, cropName, thumb);
    }
}
