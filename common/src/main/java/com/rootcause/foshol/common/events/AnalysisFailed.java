package com.rootcause.foshol.common.events;

import java.time.Instant;
import java.util.UUID;

public record AnalysisFailed(
        UUID caseId, UUID farmerId, String errorCode, String correlationId, Instant occurredAt) {}
