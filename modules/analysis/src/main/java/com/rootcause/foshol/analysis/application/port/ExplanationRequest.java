package com.rootcause.foshol.analysis.application.port;

import java.util.UUID;

public record ExplanationRequest(
        UUID caseId,
        UUID imageId,
        String cropCode,
        String objectKey,
        String sha256,
        String rawLabel,
        String correlationId) {}
