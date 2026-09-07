package com.rootcause.foshol.analysis.application.port;

public record EmbeddingResult(String modelId, float[] vector, int latencyMs) {}
