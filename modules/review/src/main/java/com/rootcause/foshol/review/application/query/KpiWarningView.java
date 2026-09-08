package com.rootcause.foshol.review.application.query;

import java.time.Instant;
import java.util.UUID;

public record KpiWarningView(UUID caseId, UUID reviewTaskId, Instant dueAt, Instant warnAt) {}
