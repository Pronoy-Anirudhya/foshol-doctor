package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.RemedyRateBasis;
import com.rootcause.foshol.common.enums.RemedyRateUnit;
import java.math.BigDecimal;

public record ComputedDoseView(
        BigDecimal amount,
        RemedyRateUnit unit,
        RemedyRateBasis basis,
        BigDecimal fromArea,
        FieldAreaUnit fromAreaUnit) {}
