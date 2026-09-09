package com.rootcause.foshol.analysis.application.port;

import java.util.UUID;

public record VisionImageRef(UUID imageId, String objectKey, String sha256) {}
