package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.intake.application.command.RecordTranscriptCommand;
import com.rootcause.foshol.intake.application.command.RecordTranscriptCommandHandler;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseIntakeApiAdapter implements CaseIntakeApi {

    private final CaseQueryPort queries;
    private final RecordTranscriptCommandHandler transcripts;

    public CaseIntakeApiAdapter(CaseQueryPort queries, RecordTranscriptCommandHandler transcripts) {
        this.queries = queries;
        this.transcripts = transcripts;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseSummary> findById(UUID caseId) {
        return queries.findSummary(caseId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID caseId, UUID farmerId) {
        return queries.findFarmerId(caseId).filter(farmerId::equals).isPresent();
    }

    @Override
    public void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence) {
        transcripts.handle(new RecordTranscriptCommand(caseId, transcriptBn, asrConfidence));
    }
}
