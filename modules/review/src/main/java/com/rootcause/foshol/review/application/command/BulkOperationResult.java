package com.rootcause.foshol.review.application.command;

import java.util.List;
import java.util.UUID;

public record BulkOperationResult(int succeeded, int failed, List<BulkItemResult> results) {

    public record BulkItemResult(UUID taskId, String status, String errorCode) {}
}
