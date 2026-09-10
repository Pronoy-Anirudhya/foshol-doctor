package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.RejectionReason;
import java.time.Instant;
import java.util.UUID;

public record CaseRejected(
        UUID caseId,
        UUID farmerId,
        UUID officerId,
        String officerName,
        RejectionReason reasonCode,
        String messageBn,
        String correlationId,
        Instant occurredAt) {}
