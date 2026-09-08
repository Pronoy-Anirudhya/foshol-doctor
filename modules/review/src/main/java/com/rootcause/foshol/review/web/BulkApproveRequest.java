package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.AdvisoryAction;
import java.util.List;
import java.util.UUID;

public record BulkApproveRequest(List<BulkApproveItem> items) {

    public record BulkApproveItem(
            UUID taskId,
            AdvisoryAction action,
            UUID diseaseId,
            List<UUID> remedyIds,
            String officerNoteBn,
            Integer expectedVersion) {}
}
