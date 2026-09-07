package com.rootcause.foshol.analysis.application.command;

import java.util.List;
import java.util.UUID;

public record RecordOfficerSymptomsCommand(UUID caseId, List<UUID> symptomIds) {}
