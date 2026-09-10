package com.rootcause.foshol.analysis.application.port;

import com.rootcause.foshol.analysis.domain.RawCandidate;
import java.util.List;

public record VisionResult(String modelId, String modelVersion, List<RawCandidate> candidates, int latencyMs) {}
