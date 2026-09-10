package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import java.time.Instant;
import java.util.UUID;

public record AdvisoryApproved(
        UUID advisoryId,
        UUID caseId,
        UUID farmerId,
        UUID officerId,
        String officerName,
        UUID diseaseId,
        String diseaseNameBn,
        AdvisoryAction action,
        int version,
        String correlationId,
        Instant occurredAt) {}
