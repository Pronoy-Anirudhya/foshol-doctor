package com.rootcause.foshol.review.domain;

import com.rootcause.foshol.common.BanglaNormalizer;
import com.rootcause.foshol.common.RejectionReason;
import java.time.Instant;
import java.util.UUID;

public final class CaseRejection {

    private static final int MAX_MESSAGE_CHARS = 500;

    private final UUID id;
    private final UUID caseId;
    private final UUID officerId;
    private final RejectionReason reasonCode;
    private final String messageBn;
    private final Instant createdAt;

    public CaseRejection(
            UUID id,
            UUID caseId,
            UUID officerId,
            RejectionReason reasonCode,
            String messageBn,
            Instant createdAt) {
        String normalised = BanglaNormalizer.forStorage(messageBn);
        if (normalised == null || normalised.isBlank()) {
            throw new ReviewException(com.rootcause.foshol.common.ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY, 400, "Rejection message must be non-blank.");
        }
        if (normalised.length() > MAX_MESSAGE_CHARS) {
            throw new ReviewException(com.rootcause.foshol.common.ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY, 400, "Rejection message must be at most 500 characters.");
        }
        if (reasonCode == null) {
            throw new ReviewException(com.rootcause.foshol.common.ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY, 400, "Rejection reason is required.");
        }
        this.id = id;
        this.caseId = caseId;
        this.officerId = officerId;
        this.reasonCode = reasonCode;
        this.messageBn = normalised;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID officerId() {
        return officerId;
    }

    public RejectionReason reasonCode() {
        return reasonCode;
    }

    public String messageBn() {
        return messageBn;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
