package com.rootcause.foshol.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.RejectionReason;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.cqrs.Command;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.review.application.command.BulkOperationResult;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommand;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.command.TransferReviewTaskCommand;
import com.rootcause.foshol.review.domain.ReviewException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkReviewServiceTest {

    @Mock
    private CommandBus commands;

    @Test
    void mixedResultsDoNotAbortBatch() {
        UUID a = Uuid7.create();
        UUID b = Uuid7.create();
        UUID target = Uuid7.create();
        UUID caller = Uuid7.create();
        when(commands.handle(any(TransferReviewTaskCommand.class))).thenAnswer(inv -> {
            TransferReviewTaskCommand command = inv.getArgument(0);
            if (b.equals(command.taskId())) {
                throw ReviewException.claimNotHeld();
            }
            return null;
        });
        BulkReviewService service = new BulkReviewService(commands, 50);
        BulkOperationResult result = service.transfer(
                caller,
                target,
                List.of(
                        new BulkReviewService.BulkTaskRef(a, 0),
                        new BulkReviewService.BulkTaskRef(b, 0)));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).hasSize(2);
    }

    @Test
    void duplicateTaskIdsFailLaterItems() {
        UUID a = Uuid7.create();
        when(commands.handle(any())).thenReturn(null);
        BulkReviewService service = new BulkReviewService(commands, 50);
        BulkOperationResult result = service.transfer(
                Uuid7.create(),
                Uuid7.create(),
                List.of(
                        new BulkReviewService.BulkTaskRef(a, 0),
                        new BulkReviewService.BulkTaskRef(a, 0)));
        assertThat(result.results().get(1).errorCode()).isEqualTo(ErrorCodes.ERR_BULK_DUPLICATE);
    }

    @Test
    void oversizeIsRejected() {
        BulkReviewService service = new BulkReviewService(commands, 1);
        assertThatThrownBy(() -> service.transfer(
                        Uuid7.create(),
                        Uuid7.create(),
                        List.of(
                                new BulkReviewService.BulkTaskRef(Uuid7.create(), 0),
                                new BulkReviewService.BulkTaskRef(Uuid7.create(), 0))))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_BULK_TOO_LARGE);
    }

    @Test
    void rejectAppliesSharedReasonAndMessageAndClaimsFirst() {
        UUID taskId = Uuid7.create();
        UUID caller = Uuid7.create();
        when(commands.handle(any())).thenReturn(null);
        BulkReviewService service = new BulkReviewService(commands, 50);
        BulkOperationResult result = service.reject(
                caller,
                RejectionReason.OTHER,
                "caller supplied message",
                List.of(new BulkReviewService.BulkRejectItem(taskId, null, null, 0)));
        assertThat(result.succeeded()).isEqualTo(1);
        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(commands, times(2)).handle(captor.capture());
        assertThat(captor.getAllValues().get(0)).isInstanceOf(ClaimReviewTaskCommand.class);
        RejectCaseCommand reject = (RejectCaseCommand) captor.getAllValues().get(1);
        assertThat(reject.reasonCode()).isEqualTo(RejectionReason.OTHER);
        assertThat(reject.messageBn()).isEqualTo("caller supplied message");
        assertThat(reject.officerId()).isEqualTo(caller);
    }

    @Test
    void rejectDoesNotRejectWhenClaimConflicts() {
        UUID taskId = Uuid7.create();
        when(commands.handle(any())).thenAnswer(inv -> {
            if (inv.getArgument(0) instanceof ClaimReviewTaskCommand) {
                throw ReviewException.claimConflict();
            }
            return null;
        });
        BulkReviewService service = new BulkReviewService(commands, 50);
        BulkOperationResult result = service.reject(
                Uuid7.create(),
                RejectionReason.OTHER,
                "caller supplied message",
                List.of(new BulkReviewService.BulkRejectItem(taskId, null, null, 0)));
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results().get(0).errorCode()).isEqualTo(ErrorCodes.ERR_CLAIM_CONFLICT);
        verify(commands, times(1)).handle(any());
    }

    @Test
    void rejectFailsItemWhenSharedMessageMissing() {
        BulkReviewService service = new BulkReviewService(commands, 50);
        BulkOperationResult result = service.reject(
                Uuid7.create(),
                RejectionReason.OTHER,
                "  ",
                List.of(new BulkReviewService.BulkRejectItem(Uuid7.create(), null, null, 0)));
        assertThat(result.results().get(0).errorCode()).isEqualTo(ErrorCodes.ERR_BAD_REQUEST);
        verify(commands, never()).handle(any());
    }
}
