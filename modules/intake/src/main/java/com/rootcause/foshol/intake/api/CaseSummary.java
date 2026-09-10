package com.rootcause.foshol.intake.api;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseSummary(
        UUID caseId,
        UUID farmerId,
        UUID cropId,
        String cropCode,
        String districtCode,
        String divisionCode,
        CaseStatus status,
        DecisionPath decisionPath,
        String noteBn,
        UUID parentCaseId,
        List<CaseImageRef> images,
        CaseAudioRef audio,
        String correlationId,
        Instant submittedAt,
        BigDecimal fieldArea,
        FieldAreaUnit fieldAreaUnit,
        BigDecimal cropQuantity,
        CropQuantityUnit cropQuantityUnit,
        MetricsSource metricsSource) {}
