package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.RejectionReason;
import java.time.Instant;
import java.util.UUID;

public record RejectionView(
        UUID caseId,
        UUID officerId,
        String officerName,
        RejectionReason reasonCode,
        String messageBn,
        Instant createdAt) {}
