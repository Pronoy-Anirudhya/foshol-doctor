package com.rootcause.foshol.analysis.application.port;

import java.util.List;
import java.util.UUID;

public record VisionBatchRequest(
        UUID caseId, String cropCode, String correlationId, List<VisionImageRef> images) {}
