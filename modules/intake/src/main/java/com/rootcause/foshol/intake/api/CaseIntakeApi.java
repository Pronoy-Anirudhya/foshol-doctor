package com.rootcause.foshol.intake.api;

import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CaseIntakeApi {

    Optional<CaseSummary> findById(UUID caseId);

    boolean isOwnedBy(UUID caseId, UUID farmerId);

    boolean officerSharesDistrict(UUID caseId, UUID officerId);

    void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence);

    void recordFieldMetrics(
            UUID caseId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource source);
}
