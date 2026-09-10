package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.enums.ReviewState;
import java.time.Instant;
import java.util.UUID;

public record ClaimReviewTaskResult(
        UUID taskId,
        UUID caseId,
        ReviewState state,
        UUID officerId,
        Instant claimedAt,
        Instant claimExpiresAt,
        Instant slaDueAt,
        Instant assignmentDueAt,
        Instant resolutionDueAt,
        short requeueCount,
        int version) {}
