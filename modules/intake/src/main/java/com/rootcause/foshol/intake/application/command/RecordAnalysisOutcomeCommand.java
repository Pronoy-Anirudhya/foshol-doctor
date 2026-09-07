package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import com.rootcause.foshol.common.DecisionPath;
import java.util.UUID;

public record RecordAnalysisOutcomeCommand(UUID caseId, DecisionPath decisionPath, boolean failed) implements Command {}
