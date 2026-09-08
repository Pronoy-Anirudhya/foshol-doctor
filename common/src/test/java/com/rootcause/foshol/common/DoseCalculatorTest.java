package com.rootcause.foshol.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DoseCalculatorTest {

    @Test
    void fixedRateIgnoresArea() {
        DoseCalculator.ComputedDose dose = DoseCalculator.compute(
                new BigDecimal("2"),
                FieldAreaUnit.DECIMAL,
                new BigDecimal("100"),
                RemedyRateUnit.ML,
                RemedyRateBasis.FIXED);
        assertThat(dose.amount()).isEqualByComparingTo("100.000");
        assertThat(dose.unit()).isEqualTo(RemedyRateUnit.ML);
    }

    @Test
    void perDecimalScalesByArea() {
        DoseCalculator.ComputedDose dose = DoseCalculator.compute(
                new BigDecimal("2"),
                FieldAreaUnit.DECIMAL,
                new BigDecimal("50"),
                RemedyRateUnit.ML,
                RemedyRateBasis.PER_DECIMAL);
        assertThat(dose.amount()).isEqualByComparingTo("100.000");
    }

    @Test
    void missingRateReturnsNull() {
        assertThat(DoseCalculator.compute(
                        new BigDecimal("1"), FieldAreaUnit.DECIMAL, null, RemedyRateUnit.ML, RemedyRateBasis.FIXED))
                .isNull();
    }
}
