package com.rootcause.foshol.intake.api;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CaseIntakeApi {

    Optional<CaseSummary> findById(UUID caseId);

    boolean isOwnedBy(UUID caseId, UUID farmerId);

    void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence);

    void recordFieldMetrics(
            UUID caseId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource source);
}
