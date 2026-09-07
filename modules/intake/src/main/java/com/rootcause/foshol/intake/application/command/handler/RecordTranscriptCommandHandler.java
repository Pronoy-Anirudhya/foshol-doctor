package com.rootcause.foshol.intake.application.command.handler;

import com.rootcause.foshol.intake.application.command.RecordTranscriptCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordTranscriptCommandHandler implements CommandHandler<RecordTranscriptCommand, Void> {

    @Override
    public Class<RecordTranscriptCommand> commandType() {
        return RecordTranscriptCommand.class;
    }

    private final DiagnosisCaseRepository cases;

    public RecordTranscriptCommandHandler(DiagnosisCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional
    @Override
    public Void handle(RecordTranscriptCommand command) {
        var diagnosisCase = cases.findById(CaseId.of(command.caseId()))
                .orElseThrow(() -> new CaseNotFoundException(CaseId.of(command.caseId())));
        diagnosisCase.recordTranscript(command.transcriptBn(), command.asrConfidence());
        cases.save(diagnosisCase);
        return null;
    }
}
