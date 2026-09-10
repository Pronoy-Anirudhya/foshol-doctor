package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.ReviewState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfficerQueueRow(
        UUID caseId,
        UUID reviewTaskId,
        String farmerName,
        String cropCode,
        String cropNameBn,
        String cropNameEn,
        boolean cropNameEnFallback,
        String districtCode,
        DecisionPath decisionPath,
        UUID topDiseaseId,
        String topDiseaseNameBn,
        String topDiseaseNameEn,
        boolean topDiseaseNameEnFallback,
        BigDecimal topConfidence,
        int imageCount,
        boolean hasAudio,
        AiMode analysisMode,
        ReviewState state,
        UUID officerId,
        boolean isResubmission,
        short requeueCount,
        Instant submittedAt,
        Instant slaDueAt,
        Instant assignmentDueAt,
        Instant resolutionDueAt) {}
