package com.rootcause.foshol.common.events;

import java.time.Instant;
import java.util.UUID;

public record KpiWarningIssued(
        UUID caseId,
        UUID reviewTaskId,
        UUID officerId,
        Instant dueAt,
        String correlationId,
        Instant occurredAt) {}
