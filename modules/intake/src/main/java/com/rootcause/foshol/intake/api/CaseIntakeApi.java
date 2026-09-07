package com.rootcause.foshol.intake.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CaseIntakeApi {

    Optional<CaseSummary> findById(UUID caseId);

    boolean isOwnedBy(UUID caseId, UUID farmerId);

    void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence);
}
