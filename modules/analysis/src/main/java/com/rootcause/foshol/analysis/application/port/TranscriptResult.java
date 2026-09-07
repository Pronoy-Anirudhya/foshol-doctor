package com.rootcause.foshol.analysis.application.port;

import java.math.BigDecimal;

public record TranscriptResult(String modelId, String transcriptBn, BigDecimal asrConfidence, int latencyMs) {}
