package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.RejectionReason;
import java.util.UUID;

public record RejectCaseCommand(UUID taskId, UUID officerId, RejectionReason reasonCode, String messageBn) {}
