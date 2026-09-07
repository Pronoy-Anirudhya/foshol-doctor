package com.rootcause.foshol.identity.domain;

import java.time.Instant;
import java.util.UUID;

public final class OtpChallenge {

    private final UUID id;
    private final String phoneHash;
    private final String codeHash;
    private int attempts;
    private final Instant expiresAt;
    private Instant consumedAt;
    private final Instant createdAt;

    public OtpChallenge(
            UUID id,
            String phoneHash,
            String codeHash,
            int attempts,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt) {
        this.id = id;
        this.phoneHash = phoneHash;
        this.codeHash = codeHash;
        this.attempts = attempts;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
    }

    public boolean isVerifiable(Instant now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }

    public boolean isExpired(Instant now) {
        return consumedAt == null && !expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public int incrementAttempts() {
        this.attempts++;
        return this.attempts;
    }

    public UUID id() {
        return id;
    }

    public String phoneHash() {
        return phoneHash;
    }

    public String codeHash() {
        return codeHash;
    }

    public int attempts() {
        return attempts;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
