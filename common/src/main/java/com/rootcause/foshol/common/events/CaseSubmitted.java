package com.rootcause.foshol.common.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseSubmitted(
        UUID caseId,
        UUID farmerId,
        UUID cropId,
        String cropCode,
        String districtCode,
        List<CaseImageRef> images,
        CaseAudioRef audio,
        String noteBn,
        UUID parentCaseId,
        String correlationId,
        Instant occurredAt) {}
