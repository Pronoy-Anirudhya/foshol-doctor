package com.rootcause.foshol.review.domain;

import com.rootcause.foshol.common.enums.KpiKind;
import java.time.Instant;
import java.util.UUID;

public record KpiBreach(
        UUID id,
        UUID reviewTaskId,
        UUID caseId,
        String districtCode,
        KpiKind kind,
        UUID officerId,
        Instant windowStartedAt,
        Instant dueAt,
        Instant breachedAt) {}
