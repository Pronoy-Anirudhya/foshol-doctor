package com.rootcause.foshol.analysis.application.port;

import com.rootcause.foshol.analysis.domain.AnalysisRun;
import com.rootcause.foshol.analysis.domain.CaseCandidate;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisPersistencePort {

    boolean hasCompletedRun(UUID caseId);

    Optional<AnalysisRun> findLatestByCaseId(UUID caseId);

    void saveNewRun(AnalysisRun run, List<CaseCandidate> candidates, List<CaseSymptom> speechSymptoms);

    void addOfficerSymptoms(UUID caseId, List<CaseSymptom> symptoms);

    boolean existsOfficerSymptom(UUID caseId, UUID symptomId);
}
