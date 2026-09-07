package com.rootcause.foshol.analysis.application.query;

import java.time.Instant;
import java.util.UUID;

public record GradcamLink(UUID caseId, UUID imageId, String url, Instant expiresAt) {}
