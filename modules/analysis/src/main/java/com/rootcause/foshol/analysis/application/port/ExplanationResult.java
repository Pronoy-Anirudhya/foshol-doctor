package com.rootcause.foshol.analysis.application.port;

public record ExplanationResult(
        String modelId, String modelVersion, byte[] overlayPng, String contentType, int latencyMs) {}
