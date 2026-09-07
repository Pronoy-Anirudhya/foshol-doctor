package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.DecisionPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisCompleted(
        UUID caseId,
        UUID farmerId,
        UUID cropId,
        DecisionPath decisionPath,
        AiMode mode,
        BigDecimal top1Confidence,
        BigDecimal margin,
        List<CandidateView> candidates,
        List<SymptomView> symptoms,
        boolean hasAudio,
        int imageCount,
        String correlationId,
        Instant occurredAt) {}
