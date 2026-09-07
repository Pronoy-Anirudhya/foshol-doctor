package com.rootcause.foshol.review.application.command;

import java.util.List;
import java.util.UUID;

public record ApproveCaseCommand(
        UUID taskId, UUID officerId, UUID diseaseId, List<UUID> remedyIds, String officerNoteBn) {}
