package com.rootcause.foshol.analysis.web;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.SymptomView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AnalysisDetailResponse(
        UUID caseId,
        String decisionPath,
        String mode,
        BigDecimal top1Confidence,
        BigDecimal top2Confidence,
        BigDecimal margin,
        List<CandidateView> candidates,
        List<SymptomView> symptoms,
        String transcriptBn,
        BigDecimal asrConfidence,
        boolean hasGradcam,
        List<String> unmappedLabels,
        String visionModelId,
        String visionModelVersion,
        int latencyMs,
        String errorCode,
        ThresholdsResponse thresholds) {

    public static AnalysisDetailResponse from(AnalysisView view, BigDecimal high, BigDecimal low) {
        return new AnalysisDetailResponse(
                view.caseId(),
                view.decisionPath().name(),
                view.mode().name(),
                view.top1Confidence(),
                view.top2Confidence(),
                view.margin(),
                view.candidates(),
                view.symptoms(),
                view.transcriptBn(),
                view.asrConfidence(),
                view.gradcamObjectKey() != null,
                view.unmappedLabels(),
                view.visionModelId(),
                view.visionModelVersion(),
                view.latencyMs(),
                view.errorCode(),
                new ThresholdsResponse(high, low));
    }

    public record ThresholdsResponse(BigDecimal high, BigDecimal low) {}
}
