package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.application.port.IdempotencyRepository;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseSubmissionWriter {

    private final DiagnosisCaseRepository cases;
    private final IdempotencyRepository idempotency;
    private final ApplicationEventPublisher events;

    public CaseSubmissionWriter(
            DiagnosisCaseRepository cases,
            IdempotencyRepository idempotency,
            ApplicationEventPublisher events) {
        this.cases = cases;
        this.idempotency = idempotency;
        this.events = events;
    }

    @Transactional
    public void write(DiagnosisCase diagnosisCase, IdempotencyRecord record, CaseSubmitted event) {
        cases.save(diagnosisCase);
        idempotency.insert(record);
        events.publishEvent(event);
    }
}
