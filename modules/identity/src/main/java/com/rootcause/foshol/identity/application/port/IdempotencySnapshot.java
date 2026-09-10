package com.rootcause.foshol.identity.application.port;

import java.time.Instant;
import java.util.UUID;

public record IdempotencySnapshot(
        UUID key, UUID officerId, String requestHash, UUID farmerId, Instant createdAt, Instant expiresAt) {}
