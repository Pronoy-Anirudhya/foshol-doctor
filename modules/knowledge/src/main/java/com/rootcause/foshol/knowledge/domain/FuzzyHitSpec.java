package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;

public final class FuzzyHitSpec {

    private final BigDecimal threshold;

    public FuzzyHitSpec(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public boolean isSatisfiedBy(BigDecimal overlap) {
        return overlap != null && overlap.compareTo(threshold) >= 0;
    }
}
