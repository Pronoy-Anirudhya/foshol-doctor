package com.rootcause.foshol.common.events;

import java.time.Instant;
import java.util.UUID;

public record AdvisoryRevised(
        UUID advisoryId,
        UUID supersededAdvisoryId,
        UUID caseId,
        UUID farmerId,
        UUID officerId,
        String officerName,
        int version,
        String correlationId,
        Instant occurredAt) {}
