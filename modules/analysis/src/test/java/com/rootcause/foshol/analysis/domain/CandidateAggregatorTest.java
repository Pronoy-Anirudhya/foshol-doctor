package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateAggregatorTest {

    private final CandidateAggregator aggregator = new CandidateAggregator();
    private final UUID d1 = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private final UUID d2 = UUID.fromString("01800000-0000-7000-8000-000000000103");

    @Test
    void maxAcrossImages() {
        List<MappedCandidate> aggregated = aggregator.aggregateAcrossImages(
                List.of(
                        List.of(new MappedCandidate(d1, "brown_spot", new BigDecimal("0.4000"))),
                        List.of(new MappedCandidate(d1, "brown_spot", new BigDecimal("0.8000"))),
                        List.of(new MappedCandidate(d1, "brown_spot", new BigDecimal("0.5500")))),
                CandidateAggregator.MAX,
                5);
        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0).confidence()).isEqualByComparingTo("0.8000");
    }

    @Test
    void withinImageKeepsHigherConfidence() {
        List<MappedCandidate> maxed = aggregator.maxWithinImage(List.of(
                new MappedCandidate(d1, "brown_spot", new BigDecimal("0.30")),
                new MappedCandidate(d1, "brown_spot", new BigDecimal("0.70"))));
        assertThat(maxed).hasSize(1);
        assertThat(maxed.get(0).confidence()).isEqualByComparingTo("0.7000");
    }

    @Test
    void tieBreakByDiseaseCodeAndTruncates() {
        UUID d0 = UUID.fromString("01800000-0000-7000-8000-000000000102");
        List<MappedCandidate> aggregated = aggregator.aggregateAcrossImages(
                List.of(List.of(
                        new MappedCandidate(d1, "brown_spot", new BigDecimal("0.50")),
                        new MappedCandidate(d2, "blast", new BigDecimal("0.50")),
                        new MappedCandidate(d0, "aaa", new BigDecimal("0.50")),
                        new MappedCandidate(UUID.fromString("01800000-0000-7000-8000-000000000104"), "tungro", new BigDecimal("0.40")),
                        new MappedCandidate(UUID.fromString("01800000-0000-7000-8000-000000000105"), "sheath_blight", new BigDecimal("0.30")),
                        new MappedCandidate(UUID.fromString("01800000-0000-7000-8000-000000000106"), "healthy", new BigDecimal("0.20")),
                        new MappedCandidate(UUID.fromString("01800000-0000-7000-8000-000000000107"), "early_blight", new BigDecimal("0.10")))),
                CandidateAggregator.MAX,
                5);
        assertThat(aggregated).hasSize(5);
        assertThat(aggregated.get(0).diseaseCode()).isEqualTo("aaa");
        assertThat(aggregated.get(1).diseaseCode()).isEqualTo("blast");
        assertThat(aggregated.get(2).diseaseCode()).isEqualTo("brown_spot");
    }
}
