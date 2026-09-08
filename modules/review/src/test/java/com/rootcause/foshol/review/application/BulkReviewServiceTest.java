package com.rootcause.foshol.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.review.application.command.BulkOperationResult;
import com.rootcause.foshol.review.application.command.TransferReviewTaskCommand;
import com.rootcause.foshol.review.domain.ReviewException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
