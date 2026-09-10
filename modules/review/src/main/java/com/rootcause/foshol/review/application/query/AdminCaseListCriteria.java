package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.KpiKind;
import java.time.Instant;
import java.util.UUID;

public record AdminCaseListCriteria(
        String districtCode,
        Instant submittedSince,
        String state,
        KpiKind kpi,
        UUID officerId,
        String cropCode,
        DecisionPath decisionPath,
        Boolean resubmission,
        int page,
        int size) {}
