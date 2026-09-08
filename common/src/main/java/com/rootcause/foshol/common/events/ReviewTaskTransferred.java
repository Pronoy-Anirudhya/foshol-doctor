package com.rootcause.foshol.common.events;

import java.time.Instant;
import java.util.UUID;

public record ReviewTaskTransferred(
        UUID caseId,
        UUID reviewTaskId,
        UUID fromOfficerId,
        UUID toOfficerId,
        String districtCode,
        String correlationId,
        Instant occurredAt) {}
