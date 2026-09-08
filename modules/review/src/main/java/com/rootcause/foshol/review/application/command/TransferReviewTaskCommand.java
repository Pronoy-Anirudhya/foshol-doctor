package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.UUID;

public record TransferReviewTaskCommand(UUID taskId, UUID fromOfficerId, UUID toOfficerId, Integer expectedVersion)
        implements Command {}
