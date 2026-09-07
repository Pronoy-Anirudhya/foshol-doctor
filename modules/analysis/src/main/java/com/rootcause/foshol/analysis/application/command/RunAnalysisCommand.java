package com.rootcause.foshol.analysis.application.command;

import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import java.util.List;
import java.util.UUID;

public record RunAnalysisCommand(
        UUID caseId,
        UUID farmerId,
        UUID cropId,
        String cropCode,
        List<CaseImageRef> images,
        CaseAudioRef audio,
        String correlationId) {}
