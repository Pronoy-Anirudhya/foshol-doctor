package com.rootcause.foshol.intake.application.command.handler;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IllegalCaseTransitionException;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ChangeCaseStatusCommandHandler implements CommandHandler<ChangeCaseStatusCommand, Void> {

    @Override
    public Class<ChangeCaseStatusCommand> commandType() {
        return ChangeCaseStatusCommand.class;
    }

    private final DiagnosisCaseRepository cases;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ChangeCaseStatusCommandHandler(
            DiagnosisCaseRepository cases, ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    @Override
    public Void handle(ChangeCaseStatusCommand command) {
        try {
            doHandle(command);
        } catch (ObjectOptimisticLockingFailureException ex) {
            doHandle(command);
        }
        return null;
    }

    private void doHandle(ChangeCaseStatusCommand command) {
        DiagnosisCase diagnosisCase = cases.findById(CaseId.of(command.caseId()))
                .orElseThrow(() -> new CaseNotFoundException(CaseId.of(command.caseId())));
        apply(diagnosisCase, command.toStatus());
    }

    void apply(DiagnosisCase diagnosisCase, CaseStatus toStatus) {
        try {
            CaseStatusChanged changed = diagnosisCase.transitionTo(toStatus, clock.instant());
            if (changed == null) {
                return;
            }
            cases.save(diagnosisCase);
            events.publishEvent(changed);
            log.info("case status {} -> {} caseId={}", changed.fromStatus(), changed.toStatus(), changed.caseId());
        } catch (IllegalCaseTransitionException ex) {
            log.warn(
                    "illegal transition ignored caseId={} from={} to={} correlationId={}",
                    ex.caseId().value(),
                    ex.fromStatus(),
                    ex.toStatus(),
                    ex.correlationId());
        }
    }
}
