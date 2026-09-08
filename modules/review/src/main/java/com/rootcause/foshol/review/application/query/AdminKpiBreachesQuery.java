package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.KpiKind;
import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record AdminKpiBreachesQuery(UUID callerId, KpiKind kind, UUID officerId, int page, int size)
        implements Query {}
