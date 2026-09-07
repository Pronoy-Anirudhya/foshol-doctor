package com.rootcause.foshol.analysis.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisApi {

    Optional<AnalysisView> findByCaseId(UUID caseId);

    void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds);
}
