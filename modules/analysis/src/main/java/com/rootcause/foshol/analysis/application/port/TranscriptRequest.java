package com.rootcause.foshol.analysis.application.port;

import java.util.UUID;

public record TranscriptRequest(
        UUID caseId, UUID audioId, String objectKey, String sha256, int durationMs, String correlationId) {}
