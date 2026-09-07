package com.rootcause.foshol.review.application.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.review.application.command.ReleaseReviewTaskCommand;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class ReleaseReviewTaskCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Test
    void releasesToPending() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        UUID officer = Uuid7.create();
        ReviewTask task = ReviewTask.createPending(Uuid7.create(), Uuid7.create(), null, t0, t0);
        task.claim(officer, t0, Duration.ofMinutes(15));
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReleaseReviewTaskCommandHandler handler =
                new ReleaseReviewTaskCommandHandler(tasks, queue, Clock.fixed(t0.plusSeconds(1), ZoneOffset.UTC), Duration.ofMinutes(15));
        handler.handle(new ReleaseReviewTaskCommand(task.id(), officer));
        verify(queue).updateState(eq(task.caseId()), eq(ReviewState.PENDING), eq(null), any());
    }
}
