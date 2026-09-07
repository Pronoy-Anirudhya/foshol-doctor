package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.Role;
import java.util.UUID;

public record CaseAudioUrlQuery(UUID caseId, UUID callerId, Role callerRole) {}
