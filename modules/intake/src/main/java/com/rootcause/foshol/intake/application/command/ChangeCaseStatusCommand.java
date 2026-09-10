package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import com.rootcause.foshol.common.enums.CaseStatus;
import java.util.UUID;

public record ChangeCaseStatusCommand(UUID caseId, CaseStatus toStatus) implements Command {}
