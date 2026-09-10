package com.rootcause.foshol.analysis.api;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.SymptomView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AnalysisView(
        UUID caseId,
        DecisionPath decisionPath,
        AiMode mode,
        BigDecimal top1Confidence,
        BigDecimal top2Confidence,
        BigDecimal margin,
        List<CandidateView> candidates,
        List<SymptomView> symptoms,
        String transcriptBn,
        BigDecimal asrConfidence,
        String gradcamObjectKey,
        List<String> unmappedLabels,
        String visionModelId,
        String visionModelVersion,
        int latencyMs,
        String errorCode) {}
