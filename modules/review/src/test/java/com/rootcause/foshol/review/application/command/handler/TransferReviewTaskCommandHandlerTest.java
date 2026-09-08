package com.rootcause.foshol.review.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.events.ReviewTaskTransferred;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.application.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.command.TransferReviewTaskCommand;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TransferReviewTaskCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private ReviewDistrictGuard districtGuard;

    @Mock
    private ApplicationEventPublisher events;

    private TransferReviewTaskCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransferReviewTaskCommandHandler(
                tasks,
                queue,
                reads,
                officers,
                districtGuard,
                events,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(30), ZoneOffset.UTC),
                Duration.ofMinutes(15));
    }

    @Test
    void transfersToSameDistrictOfficer() {
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(),
                Uuid7.create(),
                null,
                ReviewFixtures.T0.plus(Duration.ofHours(4)),
                ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15), ReviewFixtures.T0.plus(Duration.ofHours(2)));
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(officers.findById(ReviewFixtures.OFFICER_B)).thenReturn(Optional.of(ReviewFixtures.secondOfficer()));
        when(officers.findById(ReviewFixtures.OFFICER_A)).thenReturn(Optional.of(ReviewFixtures.officer()));
        when(reads.findQueueRow(task.id())).thenReturn(Optional.empty());
        var result = handler.handle(new TransferReviewTaskCommand(
                task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.OFFICER_B, 0));
        assertThat(result.state()).isEqualTo(ReviewState.CLAIMED);
        assertThat(result.officerId()).isEqualTo(ReviewFixtures.OFFICER_B);
        assertThat(result.resolutionDueAt()).isEqualTo(ReviewFixtures.T0.plus(Duration.ofHours(2)));
        verify(events).publishEvent(org.mockito.ArgumentMatchers.isA(ReviewTaskTransferred.class));
    }

    @Test
    void otherDistrictTargetIsInvalid() {
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(officers.findById(ReviewFixtures.OFFICER_B))
                .thenReturn(Optional.of(new OfficerView(
                        ReviewFixtures.OFFICER_B, "B", "CTG", "OFFICER", true, "CTG")));
        when(officers.findById(ReviewFixtures.OFFICER_A)).thenReturn(Optional.of(ReviewFixtures.officer()));
        assertThatThrownBy(() -> handler.handle(new TransferReviewTaskCommand(
                        task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.OFFICER_B, 0)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_TRANSFER_TARGET_INVALID);
    }
}
