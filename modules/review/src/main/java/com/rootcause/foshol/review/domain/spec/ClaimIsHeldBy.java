package com.rootcause.foshol.review.domain.spec;

import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ClaimIsHeldBy {

    private final UUID officerId;
    private final Instant now;
    private final Duration ttl;

    public ClaimIsHeldBy(UUID officerId, Instant now, Duration ttl) {
        this.officerId = officerId;
        this.now = now;
        this.ttl = ttl;
    }

    public boolean isSatisfiedBy(ReviewTask task) {
        return task.holdsLiveClaim(officerId, now, ttl);
    }
}
