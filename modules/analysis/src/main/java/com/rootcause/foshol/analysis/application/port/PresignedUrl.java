package com.rootcause.foshol.analysis.application.port;

import java.time.Instant;

public record PresignedUrl(String url, Instant expiresAt) {}
