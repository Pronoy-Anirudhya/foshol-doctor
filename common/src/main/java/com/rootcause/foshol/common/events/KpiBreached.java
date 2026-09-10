package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.KpiKind;
import java.time.Instant;
import java.util.UUID;

public record KpiBreached(
        KpiKind kind,
        UUID caseId,
        UUID reviewTaskId,
        String districtCode,
        UUID officerId,
        Instant dueAt,
        String correlationId,
        Instant occurredAt) {}
