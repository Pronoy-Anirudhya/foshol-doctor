package com.rootcause.foshol.review.application;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.BulkOperationResult;
import com.rootcause.foshol.review.application.command.BulkOperationResult.BulkItemResult;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.command.TransferReviewTaskCommand;
import com.rootcause.foshol.review.domain.ReviewException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BulkReviewService {

    private final CommandBus commands;
    private final int maxSize;

    public BulkReviewService(
            CommandBus commands, @Value("${" + ConfigKeys.REVIEW_BULK_MAX_SIZE + ":50}") int maxSize) {
        this.commands = commands;
        this.maxSize = maxSize;
    }

    public BulkOperationResult transfer(UUID callerId, UUID targetOfficerId, List<BulkTaskRef> items) {
        List<BulkTaskRef> normalised = requireItems(items);
        List<BulkItemResult> results = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int ok = 0;
        int failed = 0;
        for (BulkTaskRef item : normalised) {
            if (!seen.add(item.taskId())) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ErrorCodes.ERR_BULK_DUPLICATE));
                failed++;
                continue;
            }
            try {
                commands.handle(new TransferReviewTaskCommand(
                        item.taskId(), callerId, targetOfficerId, item.expectedVersion()));
                results.add(new BulkItemResult(item.taskId(), "OK", null));
                ok++;
            } catch (ReviewException ex) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ex.errorCode()));
                failed++;
            }
        }
        return new BulkOperationResult(ok, failed, results);
    }

    public BulkOperationResult approve(UUID callerId, List<BulkApproveItem> items) {
        List<BulkApproveItem> normalised = requireItems(items);
        List<BulkItemResult> results = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int ok = 0;
        int failed = 0;
        for (BulkApproveItem item : normalised) {
            if (!seen.add(item.taskId())) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ErrorCodes.ERR_BULK_DUPLICATE));
                failed++;
                continue;
            }
            try {
                commands.handle(new ApproveCaseCommand(
                        item.taskId(),
                        callerId,
                        item.diseaseId(),
                        item.remedyIds() == null ? List.of() : item.remedyIds(),
                        item.officerNoteBn()));
                results.add(new BulkItemResult(item.taskId(), "OK", null));
                ok++;
            } catch (ReviewException ex) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ex.errorCode()));
                failed++;
            }
        }
        return new BulkOperationResult(ok, failed, results);
    }

    public BulkOperationResult reject(UUID callerId, List<BulkRejectItem> items) {
        List<BulkRejectItem> normalised = requireItems(items);
        List<BulkItemResult> results = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int ok = 0;
        int failed = 0;
        for (BulkRejectItem item : normalised) {
            if (!seen.add(item.taskId())) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ErrorCodes.ERR_BULK_DUPLICATE));
                failed++;
                continue;
            }
            try {
                commands.handle(new RejectCaseCommand(item.taskId(), callerId, item.reasonCode(), item.messageBn()));
                results.add(new BulkItemResult(item.taskId(), "OK", null));
                ok++;
            } catch (ReviewException ex) {
                results.add(new BulkItemResult(item.taskId(), "FAILED", ex.errorCode()));
                failed++;
            }
        }
        return new BulkOperationResult(ok, failed, results);
    }

    private <T> List<T> requireItems(List<T> items) {
        if (items == null || items.isEmpty()) {
            throw ReviewException.bulkEmpty();
        }
        if (items.size() > maxSize) {
            throw ReviewException.bulkTooLarge();
        }
        return items;
    }

    public record BulkTaskRef(UUID taskId, Integer expectedVersion) {}

    public record BulkApproveItem(
            UUID taskId, UUID diseaseId, List<UUID> remedyIds, String officerNoteBn, Integer expectedVersion) {}

    public record BulkRejectItem(
            UUID taskId,
            com.rootcause.foshol.common.RejectionReason reasonCode,
            String messageBn,
            Integer expectedVersion) {}
}
