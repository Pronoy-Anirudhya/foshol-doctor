package com.rootcause.foshol.review.web;

import java.util.List;
import java.util.UUID;

public record BulkTransferRequest(UUID targetOfficerId, List<BulkTaskItem> items) {

    public record BulkTaskItem(UUID taskId, Integer expectedVersion) {}
}
