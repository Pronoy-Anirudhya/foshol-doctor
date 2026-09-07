package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.CaseStatus;
import java.util.UUID;

public record ChangeCaseStatusCommand(UUID caseId, CaseStatus toStatus) {}
