package com.rootcause.foshol.analysis.application.port;

public interface VisionModelPort {

    VisionResult classify(VisionRequest request);

    VisionBatchResult classifyBatch(VisionBatchRequest request);
}
