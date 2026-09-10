package com.rootcause.foshol.knowledge.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.knowledge.domain.DiseaseScore;
import com.rootcause.foshol.knowledge.domain.SymptomDeduplicator;
import com.rootcause.foshol.knowledge.domain.SymptomMatch;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InconclusiveSpecTest {

    private static final BigDecimal MIN = new BigDecimal("0.30");

    @Test
    void noSymptomsIsInconclusive() {
        assertThat(InconclusiveSpec.isInconclusive(List.of(), List.of(), MIN)).isTrue();
    }

    @Test
    void rankOneBelowMinimumIsInconclusive() {
        DiseaseScore score = new DiseaseScore(
                UUID.fromString("01800000-0000-7000-8000-000000000101"),
                "blast",
                "Blast",
                new BigDecimal("0.2500"),
                1);
        SymptomMatch match = SymptomDeduplicator.vectorHit(
                UUID.fromString("01800000-0000-7000-8000-000000000501"),
                "s1",
                "one",
                new BigDecimal("0.80"));
        assertThat(InconclusiveSpec.isInconclusive(List.of(match), List.of(score), MIN)).isTrue();
    }

    @Test
    void rankOneAtAndAboveMinimumIsConclusive() {
        SymptomMatch match = SymptomDeduplicator.vectorHit(
                UUID.fromString("01800000-0000-7000-8000-000000000501"),
                "s1",
                "one",
                new BigDecimal("0.80"));
        DiseaseScore at = new DiseaseScore(
                UUID.fromString("01800000-0000-7000-8000-000000000101"),
                "blast",
                "Blast",
                new BigDecimal("0.3000"),
                1);
        DiseaseScore above = new DiseaseScore(
                UUID.fromString("01800000-0000-7000-8000-000000000101"),
                "blast",
                "Blast",
                new BigDecimal("0.3100"),
                1);
        assertThat(InconclusiveSpec.isInconclusive(List.of(match), List.of(at), MIN)).isFalse();
        assertThat(InconclusiveSpec.isInconclusive(List.of(match), List.of(above), MIN)).isFalse();
    }
}
