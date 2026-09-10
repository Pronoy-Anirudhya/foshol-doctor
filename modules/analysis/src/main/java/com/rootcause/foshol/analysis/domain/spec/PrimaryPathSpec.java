package com.rootcause.foshol.analysis.domain.spec;

import java.math.BigDecimal;

public final class PrimaryPathSpec {

    private PrimaryPathSpec() {}

    public static boolean isSatisfied(BigDecimal top1, boolean prescribable, BigDecimal high) {
        return top1 != null && top1.compareTo(high) >= 0 && prescribable;
    }
}
