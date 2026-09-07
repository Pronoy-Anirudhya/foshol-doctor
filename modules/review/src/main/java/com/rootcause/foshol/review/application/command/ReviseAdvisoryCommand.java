package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.List;
import java.util.UUID;

public record ReviseAdvisoryCommand(
        UUID advisoryId, UUID officerId, UUID diseaseId, List<UUID> remedyIds, String officerNoteBn) implements Command {}
