package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.Role;
import java.util.UUID;

public record CaseImageUrlQuery(
        UUID caseId, UUID imageId, UUID callerId, Role callerRole, boolean derivative) {}
