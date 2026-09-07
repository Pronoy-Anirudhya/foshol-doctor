package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.CaseStatus;
import java.time.Instant;
import java.util.UUID;

public record CaseStatusChanged(
        UUID caseId,
        UUID farmerId,
        CaseStatus fromStatus,
        CaseStatus toStatus,
        String correlationId,
        Instant occurredAt) {}
