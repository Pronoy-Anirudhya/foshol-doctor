package com.rootcause.foshol.analysis.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.common.enums.Role;
import java.util.UUID;

public record AnalysisDetailQuery(UUID caseId, UUID callerId, Role role) implements Query {}
