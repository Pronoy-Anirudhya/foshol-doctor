package com.rootcause.foshol.analysis.domain.spec;

import java.math.BigDecimal;

public final class UndeterminedPathSpec {

    private UndeterminedPathSpec() {}

    public static boolean isSatisfied(
            BigDecimal top1, boolean prescribable, boolean kbInconclusive, BigDecimal high, BigDecimal low) {
        return !PrimaryPathSpec.isSatisfied(top1, prescribable, high)
                && !SecondaryPathSpec.isSatisfied(top1, kbInconclusive, high, low);
    }
}
