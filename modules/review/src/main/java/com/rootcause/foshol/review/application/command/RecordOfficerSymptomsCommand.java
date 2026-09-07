package com.rootcause.foshol.review.application.command;

import java.util.List;
import java.util.UUID;

public record RecordOfficerSymptomsCommand(UUID taskId, UUID officerId, List<UUID> symptomIds) {}
