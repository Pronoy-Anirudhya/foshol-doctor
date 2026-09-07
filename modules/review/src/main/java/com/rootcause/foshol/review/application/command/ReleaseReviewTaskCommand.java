package com.rootcause.foshol.review.application.command;

import java.util.UUID;

public record ReleaseReviewTaskCommand(UUID taskId, UUID officerId) {}
