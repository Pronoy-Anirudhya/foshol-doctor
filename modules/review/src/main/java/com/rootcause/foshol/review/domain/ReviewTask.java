package com.rootcause.foshol.review.domain;

import com.rootcause.foshol.common.ReviewState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ReviewTask {

    private final UUID id;
    private final UUID caseId;
    private UUID officerId;
    private ReviewState state;
    private final BigDecimal priorityConfidence;
    private Instant claimedAt;
    private final Instant slaDueAt;
    private short requeueCount;
    private final int version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final String createdBy;
    private String updatedBy;

    public ReviewTask(
            UUID id,
            UUID caseId,
            UUID officerId,
            ReviewState state,
            BigDecimal priorityConfidence,
            Instant claimedAt,
            Instant slaDueAt,
            short requeueCount,
            int version,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy) {
        this.id = id;
        this.caseId = caseId;
        this.officerId = officerId;
        this.state = state;
        this.priorityConfidence = priorityConfidence;
        this.claimedAt = claimedAt;
        this.slaDueAt = slaDueAt;
        this.requeueCount = requeueCount;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public static ReviewTask createPending(
            UUID id, UUID caseId, BigDecimal priorityConfidence, Instant slaDueAt, Instant now) {
        return new ReviewTask(
                id,
                caseId,
                null,
                ReviewState.PENDING,
                priorityConfidence,
                null,
                slaDueAt,
                (short) 0,
                0,
                now,
                now,
                "system",
                "system");
    }

    public void claim(UUID officerId, Instant now, Duration ttl) {
        if (isTerminal()) {
            throw ReviewException.terminal();
        }
        if (state == ReviewState.CLAIMED && this.officerId != null && !isClaimExpired(now, ttl)) {
            if (this.officerId.equals(officerId)) {
                this.claimedAt = now;
                this.updatedAt = now;
                this.updatedBy = officerId.toString();
                return;
            }
            throw ReviewException.claimConflict();
        }
        this.state = ReviewState.CLAIMED;
        this.officerId = officerId;
        this.claimedAt = now;
        this.updatedAt = now;
        this.updatedBy = officerId.toString();
    }

    public void release(UUID officerId, Instant now, Duration ttl) {
        requireLiveClaim(officerId, now, ttl);
        this.state = ReviewState.PENDING;
        this.officerId = null;
        this.claimedAt = null;
        this.updatedAt = now;
        this.updatedBy = officerId.toString();
    }

    public void markDone(Instant now, String actor) {
        if (isTerminal()) {
            throw ReviewException.terminal();
        }
        this.state = ReviewState.DONE;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markRejected(Instant now, String actor) {
        if (isTerminal()) {
            throw ReviewException.terminal();
        }
        this.state = ReviewState.REJECTED;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void reclaimForRevision(UUID officerId, Instant now) {
        if (state == ReviewState.REJECTED) {
            throw ReviewException.terminal();
        }
        this.state = ReviewState.CLAIMED;
        this.officerId = officerId;
        this.claimedAt = now;
        this.updatedAt = now;
        this.updatedBy = officerId.toString();
    }

    public boolean sweepExpired(Instant now, Duration ttl) {
        if (state != ReviewState.CLAIMED || !isClaimExpired(now, ttl)) {
            return false;
        }
        this.state = ReviewState.PENDING;
        this.officerId = null;
        this.claimedAt = null;
        this.requeueCount = (short) (requeueCount + 1);
        this.updatedAt = now;
        this.updatedBy = "system";
        return true;
    }

    public void requireLiveClaim(UUID officerId, Instant now, Duration ttl) {
        if (isTerminal()) {
            throw ReviewException.terminal();
        }
        if (!holdsLiveClaim(officerId, now, ttl)) {
            throw ReviewException.claimNotHeld();
        }
    }

    public boolean isTerminal() {
        return state == ReviewState.DONE || state == ReviewState.REJECTED;
    }

    public boolean isClaimExpired(Instant now, Duration ttl) {
        return claimedAt == null || !now.isBefore(claimedAt.plus(ttl));
    }

    public boolean holdsLiveClaim(UUID officerId, Instant now, Duration ttl) {
        return state == ReviewState.CLAIMED && officerId.equals(this.officerId) && !isClaimExpired(now, ttl);
    }

    public boolean isHeldBy(UUID officerId, Instant now, Duration ttl) {
        return holdsLiveClaim(officerId, now, ttl);
    }

    public Instant claimExpiresAt(Duration ttl) {
        return claimedAt == null ? null : claimedAt.plus(ttl);
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID officerId() {
        return officerId;
    }

    public ReviewState state() {
        return state;
    }

    public BigDecimal priorityConfidence() {
        return priorityConfidence;
    }

    public Instant claimedAt() {
        return claimedAt;
    }

    public Instant slaDueAt() {
        return slaDueAt;
    }

    public short requeueCount() {
        return requeueCount;
    }

    public int version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }
}
