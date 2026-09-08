package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.RemedyRateBasis;
import com.rootcause.foshol.common.RemedyRateUnit;
import java.math.BigDecimal;

public record ComputedDoseView(
        BigDecimal amount,
        RemedyRateUnit unit,
        RemedyRateBasis basis,
        BigDecimal fromArea,
        FieldAreaUnit fromAreaUnit) {}
