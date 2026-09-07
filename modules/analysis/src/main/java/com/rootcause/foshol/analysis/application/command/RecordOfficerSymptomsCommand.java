package com.rootcause.foshol.analysis.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.List;
import java.util.UUID;

public record RecordOfficerSymptomsCommand(UUID caseId, List<UUID> symptomIds) implements Command {}
