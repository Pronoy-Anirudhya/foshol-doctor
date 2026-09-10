package com.rootcause.foshol.review.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.review.application.command.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.command.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommand;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskResult;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ClaimReviewTaskCommandHandlerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Mock
    private com.rootcause.foshol.review.application.command.ReviewDistrictGuard districtGuard;

    private ClaimReviewTaskCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClaimReviewTaskCommandHandler(
                tasks, queue, districtGuard, ReviewKpiCalendar.alwaysOpenUtc(), Clock.fixed(T0, ZoneOffset.UTC), TTL);
    }

    @Test
    void claimsPendingTask() {
        ReviewTask task = pending();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ClaimReviewTaskResult result =
                handler.handle(new ClaimReviewTaskCommand(task.id(), Uuid7.create()));
        assertThat(result.state()).isEqualTo(ReviewState.CLAIMED);
        verify(queue).updateState(eq(task.caseId()), eq(ReviewState.CLAIMED), any(), eq(T0));
    }

    @Test
    void missingTask() {
        UUID id = Uuid7.create();
        when(tasks.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(new ClaimReviewTaskCommand(id, Uuid7.create())))
                .isInstanceOf(ReviewException.class)
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REVIEW_TASK_NOT_FOUND);
    }

    @Test
    void optimisticLockBecomesConflict() {
        ReviewTask task = pending();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenThrow(new OptimisticLockingFailureException("stale"));
        assertThatThrownBy(() -> handler.handle(new ClaimReviewTaskCommand(task.id(), Uuid7.create())))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CLAIM_CONFLICT);
    }

    private static ReviewTask pending() {
        return ReviewTask.createPending(Uuid7.create(), Uuid7.create(), null, T0.plus(Duration.ofHours(4)), T0);
    }
}
