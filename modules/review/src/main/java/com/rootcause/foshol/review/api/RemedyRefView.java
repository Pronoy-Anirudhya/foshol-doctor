package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.enums.RemedyRateBasis;
import com.rootcause.foshol.common.enums.RemedyRateUnit;
import com.rootcause.foshol.common.enums.RemedyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RemedyRefView(
        UUID remedyId,
        RemedyType type,
        String titleBn,
        List<String> stepsBn,
        String dosageBn,
        Integer phiDays,
        String sourceRef,
        BigDecimal rateAmount,
        RemedyRateUnit rateUnit,
        RemedyRateBasis rateBasis,
        String rateNotesBn,
        ComputedDoseView computedDose) {}
