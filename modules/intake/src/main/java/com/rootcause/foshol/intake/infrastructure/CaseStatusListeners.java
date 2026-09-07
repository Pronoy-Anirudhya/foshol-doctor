package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommand;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommandHandler;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommand;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommandHandler;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class CaseStatusListeners {

    private static final Logger log = LoggerFactory.getLogger(CaseStatusListeners.class);

    private final ChangeCaseStatusCommandHandler statusHandler;
    private final RecordAnalysisOutcomeCommandHandler analysisHandler;
    private final DiagnosisCaseRepository cases;
    private final KnowledgeQueryApi knowledge;

    public CaseStatusListeners(
            ChangeCaseStatusCommandHandler statusHandler,
            RecordAnalysisOutcomeCommandHandler analysisHandler,
            DiagnosisCaseRepository cases,
            KnowledgeQueryApi knowledge) {
        this.statusHandler = statusHandler;
        this.analysisHandler = analysisHandler;
        this.cases = cases;
        this.knowledge = knowledge;
    }

    @ApplicationModuleListener
    public void onSubmitted(CaseSubmitted event) {
        safeStatus(event.caseId(), CaseStatus.ANALYSING);
    }

    @ApplicationModuleListener
    public void onAnalysisCompleted(AnalysisCompleted event) {
        try {
            analysisHandler.handle(new RecordAnalysisOutcomeCommand(event.caseId(), event.decisionPath(), false));
        } catch (CaseNotFoundException ex) {
            log.warn("analysis completed for unknown caseId={}", event.caseId());
        }
        cases.findById(CaseId.of(event.caseId())).ifPresent(this::persistHistory);
    }

    @ApplicationModuleListener
    public void onAnalysisFailed(AnalysisFailed event) {
        try {
            analysisHandler.handle(new RecordAnalysisOutcomeCommand(event.caseId(), null, true));
        } catch (CaseNotFoundException ex) {
            log.warn("analysis failed for unknown caseId={}", event.caseId());
        }
    }

    @ApplicationModuleListener
    public void onAdvisoryApproved(AdvisoryApproved event) {
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
        safeStatus(event.caseId(), CaseStatus.REJECTED);
        cases.enrichRejection(event.caseId(), event.messageBn());
    }

    @ApplicationModuleListener
    public void onStatusChanged(CaseStatusChanged event) {
        cases.findById(CaseId.of(event.caseId())).ifPresent(this::persistHistory);
    }

    private void safeStatus(java.util.UUID caseId, CaseStatus target) {
        try {
            statusHandler.handle(new ChangeCaseStatusCommand(caseId, target));
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
