package com.rootcause.foshol.knowledge.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuzzyHitSpecTest {

    @Test
    void retainsOverlapsAtOrAboveThreshold() {
        FuzzyHitSpec spec = new FuzzyHitSpec(new BigDecimal("0.60"));
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.667"))).isTrue();
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.5"))).isFalse();
    }
}
