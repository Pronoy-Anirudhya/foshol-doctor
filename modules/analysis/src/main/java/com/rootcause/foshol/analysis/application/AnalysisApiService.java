package com.rootcause.foshol.analysis.application;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.command.RecordOfficerSymptomsCommand;
import com.rootcause.foshol.analysis.application.command.RecordOfficerSymptomsCommandHandler;
import com.rootcause.foshol.analysis.application.query.AnalysisReadRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnalysisApiService implements AnalysisApi {

    private final AnalysisReadRepository reads;
    private final RecordOfficerSymptomsCommandHandler officerSymptoms;

    public AnalysisApiService(
            AnalysisReadRepository reads, RecordOfficerSymptomsCommandHandler officerSymptoms) {
        this.reads = reads;
        this.officerSymptoms = officerSymptoms;
    }

    @Override
    public Optional<AnalysisView> findByCaseId(UUID caseId) {
        return reads.findByCaseId(caseId);
    }

    @Override
    public void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds) {
        officerSymptoms.handle(new RecordOfficerSymptomsCommand(caseId, symptomIds));
    }
}
