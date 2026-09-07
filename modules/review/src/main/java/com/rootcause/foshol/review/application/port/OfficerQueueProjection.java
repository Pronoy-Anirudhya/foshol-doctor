package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ReviewState;
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
        Instant slaDueAt) {}
