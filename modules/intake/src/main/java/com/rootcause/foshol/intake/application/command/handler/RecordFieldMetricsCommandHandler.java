package com.rootcause.foshol.intake.application.command.handler;

import com.rootcause.foshol.intake.application.command.RecordFieldMetricsCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordFieldMetricsCommandHandler implements CommandHandler<RecordFieldMetricsCommand, Void> {

    @Override
    public Class<RecordFieldMetricsCommand> commandType() {
        return RecordFieldMetricsCommand.class;
    }

    private final DiagnosisCaseRepository cases;

    public RecordFieldMetricsCommandHandler(DiagnosisCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional
    @Override
    public Void handle(RecordFieldMetricsCommand command) {
        var diagnosisCase = cases.findById(CaseId.of(command.caseId()))
                .orElseThrow(() -> new CaseNotFoundException(CaseId.of(command.caseId())));
        diagnosisCase.recordFieldMetrics(
                command.fieldArea(),
                command.fieldAreaUnit(),
                command.cropQuantity(),
                command.cropQuantityUnit(),
                command.source());
        cases.save(diagnosisCase);
        return null;
    }
}
