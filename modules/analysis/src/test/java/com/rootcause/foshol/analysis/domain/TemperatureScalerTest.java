package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemperatureScalerTest {

    @Test
    void identityWhenTemperatureIsOne() {
        List<RawCandidate> input = List.of(
                new RawCandidate("a", new BigDecimal("0.7000")),
                new RawCandidate("b", new BigDecimal("0.3000")));
        assertThat(TemperatureScaler.rescale(input, BigDecimal.ONE)).isEqualTo(input);
    }

    @Test
    void renormalisesWhenTemperatureIsNotOne() {
        List<RawCandidate> input = List.of(
                new RawCandidate("a", new BigDecimal("0.7000")),
                new RawCandidate("b", new BigDecimal("0.3000")));
        List<RawCandidate> scaled = TemperatureScaler.rescale(input, new BigDecimal("2.0"));
        BigDecimal sum = scaled.stream().map(RawCandidate::confidence).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1.0000");
    }
}
