package com.rootcause.foshol.review.application.command;

import java.util.UUID;

public record ClaimReviewTaskCommand(UUID taskId, UUID officerId) {}
