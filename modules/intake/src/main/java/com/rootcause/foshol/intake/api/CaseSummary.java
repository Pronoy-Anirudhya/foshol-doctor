package com.rootcause.foshol.intake.api;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseSummary(
        UUID caseId,
        UUID farmerId,
        UUID cropId,
        String cropCode,
        String districtCode,
        CaseStatus status,
        DecisionPath decisionPath,
        String noteBn,
        UUID parentCaseId,
        List<CaseImageRef> images,
        CaseAudioRef audio,
        String correlationId,
        Instant submittedAt) {}
