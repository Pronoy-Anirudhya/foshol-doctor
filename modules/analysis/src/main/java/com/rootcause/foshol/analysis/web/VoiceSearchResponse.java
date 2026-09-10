package com.rootcause.foshol.analysis.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VoiceSearchResponse(
        String transcription,
        BigDecimal asrConfidence,
        List<VoiceSearchCandidateResponse> candidates,
        boolean inconclusive) {}
