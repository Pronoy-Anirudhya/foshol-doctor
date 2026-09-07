package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
        UUID key,
        UUID farmerId,
        String endpoint,
        Sha256 requestHash,
        int responseStatus,
        String responseBody,
        Instant createdAt,
        Instant expiresAt) {}
