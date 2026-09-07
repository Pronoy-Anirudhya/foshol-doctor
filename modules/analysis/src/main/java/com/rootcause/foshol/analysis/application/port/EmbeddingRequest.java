package com.rootcause.foshol.analysis.application.port;

import java.util.UUID;

public record EmbeddingRequest(UUID caseId, String normalisedText, String correlationId) {}
