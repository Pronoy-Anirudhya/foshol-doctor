package com.rootcause.foshol.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainInvariantTest {

    @Test
    void weightMustBeInsideUnitInterval() {
        UUID d = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> new DiseaseSymptomWeight(d, s, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiseaseSymptomWeight(d, s, new BigDecimal("1.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
