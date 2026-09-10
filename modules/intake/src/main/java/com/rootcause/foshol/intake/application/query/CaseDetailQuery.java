package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.common.enums.Role;
import java.util.UUID;

public record CaseDetailQuery(UUID caseId, UUID callerId, Role callerRole) implements Query {}
