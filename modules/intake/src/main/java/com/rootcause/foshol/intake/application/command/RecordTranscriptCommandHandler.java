package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordTranscriptCommandHandler {

    private final DiagnosisCaseRepository cases;

    public RecordTranscriptCommandHandler(DiagnosisCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional
    public void handle(RecordTranscriptCommand command) {
        var diagnosisCase = cases.findById(CaseId.of(command.caseId()))
                .orElseThrow(() -> new CaseNotFoundException(CaseId.of(command.caseId())));
        diagnosisCase.recordTranscript(command.transcriptBn(), command.asrConfidence());
        cases.save(diagnosisCase);
    }
}
