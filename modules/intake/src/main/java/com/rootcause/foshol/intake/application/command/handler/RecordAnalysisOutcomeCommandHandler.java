package com.rootcause.foshol.intake.application.command.handler;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordAnalysisOutcomeCommandHandler implements CommandHandler<RecordAnalysisOutcomeCommand, Void> {

    @Override
    public Class<RecordAnalysisOutcomeCommand> commandType() {
        return RecordAnalysisOutcomeCommand.class;
    }

    private final DiagnosisCaseRepository cases;
    private final ChangeCaseStatusCommandHandler statusHandler;

    public RecordAnalysisOutcomeCommandHandler(
            DiagnosisCaseRepository cases, ChangeCaseStatusCommandHandler statusHandler) {
        this.cases = cases;
        this.statusHandler = statusHandler;
    }

    @Transactional
    @Override
    public Void handle(RecordAnalysisOutcomeCommand command) {
        try {
            apply(command);
        } catch (ObjectOptimisticLockingFailureException ex) {
            apply(command);
        }
        return null;
    }

    private void apply(RecordAnalysisOutcomeCommand command) {
        DiagnosisCase diagnosisCase = cases.findById(CaseId.of(command.caseId()))
                .orElseThrow(() -> new CaseNotFoundException(CaseId.of(command.caseId())));
        if (command.failed()) {
            statusHandler.apply(diagnosisCase, CaseStatus.FAILED);
            return;
        }
        diagnosisCase.recordDecisionPath(command.decisionPath());
        cases.save(diagnosisCase);
        if (diagnosisCase.status() == CaseStatus.ANALYSING) {
            statusHandler.apply(diagnosisCase, CaseStatus.ANALYSED);
            statusHandler.apply(diagnosisCase, CaseStatus.IN_REVIEW);
        } else if (diagnosisCase.status() == CaseStatus.ANALYSED) {
            statusHandler.apply(diagnosisCase, CaseStatus.IN_REVIEW);
        }
    }
}
