package com.rootcause.foshol.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewTaskTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final UUID OFFICER_A = Uuid7.create();
    private static final UUID OFFICER_B = Uuid7.create();

    @Test
    void pendingHasNoOfficerOrClaim() {
        ReviewTask task = pending();
        assertThat(task.state()).isEqualTo(ReviewState.PENDING);
        assertThat(task.officerId()).isNull();
        assertThat(task.claimedAt()).isNull();
        assertThat(task.requeueCount()).isZero();
    }

    @Test
    void claimSetsOfficerAndTimestamp() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        assertThat(task.state()).isEqualTo(ReviewState.CLAIMED);
        assertThat(task.officerId()).isEqualTo(OFFICER_A);
        assertThat(task.claimedAt()).isEqualTo(T0);
    }

    @Test
    void holderRefreshDoesNotChangeRequeueCount() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        task.claim(OFFICER_A, T0.plus(Duration.ofMinutes(3)), TTL);
        assertThat(task.claimedAt()).isEqualTo(T0.plus(Duration.ofMinutes(3)));
        assertThat(task.requeueCount()).isZero();
    }

    @Test
    void otherOfficerCannotClaimLiveTask() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        assertThatThrownBy(() -> task.claim(OFFICER_B, T0.plusSeconds(1), TTL))
                .isInstanceOf(ReviewException.class)
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CLAIM_CONFLICT);
    }

    @Test
    void releaseReturnsToPendingWithoutIncrementingRequeue() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        task.release(OFFICER_A, T0.plusSeconds(10), TTL);
        assertThat(task.state()).isEqualTo(ReviewState.PENDING);
        assertThat(task.officerId()).isNull();
        assertThat(task.claimedAt()).isNull();
        assertThat(task.requeueCount()).isZero();
    }

    @Test
    void expiredClaimConfersNoAuthority() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        Instant later = T0.plus(TTL).plusSeconds(1);
        assertThat(task.isClaimExpired(later, TTL)).isTrue();
        assertThatThrownBy(() -> task.requireLiveClaim(OFFICER_A, later, TTL))
                .isInstanceOf(ReviewException.class)
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CLAIM_NOT_HELD);
    }

    @Test
    void sweepExpiredIncrementsRequeueAndClearsClaim() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        task.sweepExpired(T0.plus(TTL).plusSeconds(1), TTL);
        assertThat(task.state()).isEqualTo(ReviewState.PENDING);
        assertThat(task.officerId()).isNull();
        assertThat(task.requeueCount()).isEqualTo((short) 1);
    }

    @Test
    void sweeperIgnoresDone() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        task.markDone(T0.plusSeconds(1), OFFICER_A.toString());
        task.sweepExpired(T0.plus(TTL).plusSeconds(1), TTL);
        assertThat(task.state()).isEqualTo(ReviewState.DONE);
        assertThat(task.requeueCount()).isZero();
    }

    @Test
    void doneAndRejectedAreTerminal() {
        ReviewTask done = pending();
        done.claim(OFFICER_A, T0, TTL);
        done.markDone(T0.plusSeconds(1), OFFICER_A.toString());
        assertThatThrownBy(() -> done.claim(OFFICER_B, T0.plusSeconds(2), TTL))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_TASK_TERMINAL);

        ReviewTask rejected = pending();
        rejected.claim(OFFICER_A, T0, TTL);
        rejected.markRejected(T0.plusSeconds(1), OFFICER_A.toString());
        assertThatThrownBy(() -> rejected.release(OFFICER_A, T0.plusSeconds(2), TTL))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_TASK_TERMINAL);
    }

    @Test
    void reclaimForRevisionMovesDoneToClaimed() {
        ReviewTask task = pending();
        task.claim(OFFICER_A, T0, TTL);
        task.markDone(T0.plusSeconds(1), OFFICER_A.toString());
        task.reclaimForRevision(OFFICER_A, T0.plusSeconds(2));
        assertThat(task.state()).isEqualTo(ReviewState.CLAIMED);
        assertThat(task.officerId()).isEqualTo(OFFICER_A);
    }

    @Test
    void slaDueAtIsImmutableOnClaim() {
        Instant sla = T0.plus(Duration.ofHours(4));
        ReviewTask task = ReviewTask.createPending(Uuid7.create(), Uuid7.create(), BigDecimal.ONE, sla, T0);
        task.claim(OFFICER_A, T0, TTL);
        assertThat(task.slaDueAt()).isEqualTo(sla);
    }

    private static ReviewTask pending() {
        return ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), new BigDecimal("0.5000"), T0.plus(Duration.ofHours(4)), T0);
    }
}
