package com.rootcause.foshol.analysis.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.common.Role;
import java.util.UUID;

public record GradcamLinkQuery(UUID caseId, UUID callerId, Role role) implements Query {}
