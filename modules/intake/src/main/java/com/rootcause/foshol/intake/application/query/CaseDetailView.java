package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseDetailView(
        UUID caseId,
        UUID cropId,
        String cropNameBn,
        CaseStatus status,
        DecisionPath decisionPath,
        String noteBn,
        UUID parentCaseId,
        List<ImageRefView> images,
        AudioRefView audio,
        Instant submittedAt,
        BigDecimal fieldArea,
        FieldAreaUnit fieldAreaUnit,
        BigDecimal cropQuantity,
        CropQuantityUnit cropQuantityUnit,
        MetricsSource metricsSource) {

    public record ImageRefView(
            UUID imageId, int position, boolean primary, BigDecimal qualityScore, Integer width, Integer height) {}

    public record AudioRefView(UUID audioId, int durationMs, String transcriptBn) {}
}
