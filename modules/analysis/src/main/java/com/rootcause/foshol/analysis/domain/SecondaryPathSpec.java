package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;

public final class SecondaryPathSpec {

    private SecondaryPathSpec() {}

    public static boolean isSatisfied(BigDecimal top1, BigDecimal high, BigDecimal low) {
        return top1 != null && top1.compareTo(low) >= 0 && top1.compareTo(high) < 0;
    }

    public static boolean isSatisfied(BigDecimal top1, boolean kbInconclusive, BigDecimal high, BigDecimal low) {
        return isSatisfied(top1, high, low);
    }
}
