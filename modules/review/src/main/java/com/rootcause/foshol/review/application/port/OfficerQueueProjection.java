package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.ReviewState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfficerQueueProjection(
        UUID caseId,
        UUID reviewTaskId,
        String farmerName,
        String cropCode,
        String cropNameBn,
        String districtCode,
        String divisionCode,
        DecisionPath decisionPath,
        UUID topDiseaseId,
        String topDiseaseNameBn,
        BigDecimal topConfidence,
        int imageCount,
        boolean hasAudio,
        AiMode analysisMode,
        ReviewState state,
        UUID officerId,
        boolean resubmission,
        Instant submittedAt,
        Instant slaDueAt,
        Instant assignmentDueAt,
        Instant resolutionDueAt) {}
