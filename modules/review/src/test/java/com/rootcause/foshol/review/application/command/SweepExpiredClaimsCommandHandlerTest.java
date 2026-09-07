package com.rootcause.foshol.review.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SweepExpiredClaimsCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Test
    void sweepsExpiredAndIgnoresDone() {
        ReviewTask expired = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        expired.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        ReviewTask done = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        done.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        done.markDone(ReviewFixtures.T0.plusSeconds(1), ReviewFixtures.OFFICER_A.toString());
        when(tasks.lockExpiredClaims(any())).thenReturn(List.of(expired, done));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SweepExpiredClaimsCommandHandler handler = new SweepExpiredClaimsCommandHandler(
                tasks,
                queue,
                Clock.fixed(ReviewFixtures.T0.plus(Duration.ofMinutes(16)), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        int swept = handler.handle();
        assertThat(swept).isEqualTo(1);
        assertThat(expired.state()).isEqualTo(ReviewState.PENDING);
        assertThat(expired.requeueCount()).isEqualTo((short) 1);
        assertThat(done.state()).isEqualTo(ReviewState.DONE);
        verify(queue).updateState(expired.caseId(), ReviewState.PENDING, null, ReviewFixtures.T0.plus(Duration.ofMinutes(16)));
        verify(queue, never()).updateState(done.caseId(), ReviewState.PENDING, null, ReviewFixtures.T0.plus(Duration.ofMinutes(16)));
    }
}
