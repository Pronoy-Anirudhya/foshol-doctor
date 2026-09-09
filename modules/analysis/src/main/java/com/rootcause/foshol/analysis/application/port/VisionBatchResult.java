package com.rootcause.foshol.analysis.application.port;

import java.util.List;

public record VisionBatchResult(
        String modelId, String modelVersion, List<VisionImageResult> images, int latencyMs) {}
