package com.rootcause.foshol.analysis.application.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catalogue candidates only. After the farmer confirms {@code diseaseId}, load registered
 * remedies from {@code GET /api/v1/diseases/{diseaseId}/remedies}. This result never includes
 * remedy text.
 */
public record VoiceKbLookupResult(
        String transcription,
        BigDecimal asrConfidence,
        List<VoiceKbDiseaseCandidate> candidates,
        boolean inconclusive) {}
