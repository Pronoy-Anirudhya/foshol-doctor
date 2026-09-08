package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.RejectionReason;
import java.util.List;
import java.util.UUID;

public record BulkRejectRequest(RejectionReason reasonCode, String messageBn, List<BulkRejectItem> items) {

    public record BulkRejectItem(
            UUID taskId, RejectionReason reasonCode, String messageBn, Integer expectedVersion) {}
}
