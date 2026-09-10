package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.common.enums.Role;
import java.util.UUID;

public record CaseImageUrlQuery(
        UUID caseId, UUID imageId, UUID callerId, Role callerRole, boolean derivative) implements Query {}
