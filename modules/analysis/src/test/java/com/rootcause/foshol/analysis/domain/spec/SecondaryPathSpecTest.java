package com.rootcause.foshol.analysis.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SecondaryPathSpecTest {

    private static final BigDecimal HIGH = new BigDecimal("0.75");
    private static final BigDecimal LOW = new BigDecimal("0.45");

    @Test
    void satisfiedAtLowInclusiveAndBelowHigh() {
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.45"), HIGH, LOW)).isTrue();
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.60"), HIGH, LOW)).isTrue();
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.7499"), HIGH, LOW)).isTrue();
    }

    @Test
    void notSatisfiedAtHighOrBelowLowOrWhenUnmapped() {
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.75"), HIGH, LOW)).isFalse();
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.44"), HIGH, LOW)).isFalse();
        assertThat(SecondaryPathSpec.isSatisfied(null, HIGH, LOW)).isFalse();
    }

    @Test
    void kbInconclusiveDoesNotGateSecondary() {
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.60"), true, HIGH, LOW)).isTrue();
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.60"), false, HIGH, LOW)).isTrue();
        assertThat(SecondaryPathSpec.isSatisfied(new BigDecimal("0.44"), true, HIGH, LOW)).isFalse();
    }
}
