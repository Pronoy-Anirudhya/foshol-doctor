package com.rootcause.foshol.analysis.application.port;

import java.util.List;
import java.util.UUID;

public record VisionImageResult(UUID imageId, String sha256, List<RawCandidate> candidates) {}
