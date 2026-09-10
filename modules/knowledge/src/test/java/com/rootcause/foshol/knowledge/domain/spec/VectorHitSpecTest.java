package com.rootcause.foshol.knowledge.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VectorHitSpecTest {

    @Test
    void retainsRowsAtOrAboveThreshold() {
        VectorHitSpec spec = new VectorHitSpec(new BigDecimal("0.72"));
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.90"))).isTrue();
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.75"))).isTrue();
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.72"))).isTrue();
        assertThat(spec.isSatisfiedBy(new BigDecimal("0.60"))).isFalse();
    }
}
