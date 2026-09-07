package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;

public final class VectorHitSpec {

    private final BigDecimal threshold;

    public VectorHitSpec(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public boolean isSatisfiedBy(BigDecimal similarity) {
        return similarity != null && similarity.compareTo(threshold) >= 0;
    }
}
