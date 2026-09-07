package com.rootcause.foshol.intake.application.query;

import java.time.Instant;

public record PresignedUrlView(String url, Instant expiresAt) {}
