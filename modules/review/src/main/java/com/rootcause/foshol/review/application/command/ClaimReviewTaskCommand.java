package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.UUID;

public record ClaimReviewTaskCommand(UUID taskId, UUID officerId) implements Command {}
