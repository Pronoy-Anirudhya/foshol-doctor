package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.KpiKind;
import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record AdminCasesQuery(
        UUID callerId,
        AdminCasePeriod period,
        String state,
        KpiKind kpi,
        UUID officerId,
        String cropCode,
        DecisionPath decisionPath,
        Boolean resubmission,
        int page,
        int size)
        implements Query {}
