package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MergeRankerTest {

    private final UUID d1 = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private final UUID d2 = UUID.fromString("01800000-0000-7000-8000-000000000102");
    private final UUID d3 = UUID.fromString("01800000-0000-7000-8000-000000000103");

    @Test
    void averagesUnionOfSources() {
        List<RankedCandidate> vision = List.of(
                new RankedCandidate(d1, new BigDecimal("0.60"), 1, "d1"),
                new RankedCandidate(d2, new BigDecimal("0.20"), 2, "d2"));
        List<ScoredDiseaseScore> kb = List.of(
                new ScoredDiseaseScore(d1, new BigDecimal("0.40"), "d1"),
                new ScoredDiseaseScore(d3, new BigDecimal("0.80"), "d3"));
        List<RankedCandidate> merged = MergeRanker.merge(
                vision, kb, Map.of(d1, "d1", d2, "d2", d3, "d3"), 5);
        assertThat(merged).extracting(RankedCandidate::diseaseId).containsExactly(d1, d3, d2);
        assertThat(merged.get(0).confidence()).isEqualByComparingTo("0.5000");
        assertThat(merged.get(1).confidence()).isEqualByComparingTo("0.4000");
        assertThat(merged.get(2).confidence()).isEqualByComparingTo("0.1000");
    }

    @Test
    void higherVisionWinsEqualMerged() {
        UUID a = UUID.fromString("01800000-0000-7000-8000-000000000111");
        UUID b = UUID.fromString("01800000-0000-7000-8000-000000000112");
        List<RankedCandidate> vision = List.of(
                new RankedCandidate(a, new BigDecimal("0.80"), 1, "a"),
                new RankedCandidate(b, new BigDecimal("0.20"), 2, "b"));
        List<ScoredDiseaseScore> kb = List.of(
                new ScoredDiseaseScore(a, new BigDecimal("0.20"), "a"),
                new ScoredDiseaseScore(b, new BigDecimal("0.80"), "b"));
        List<RankedCandidate> merged =
                MergeRanker.merge(vision, kb, Map.of(a, "a", b, "b"), 5);
        assertThat(merged.get(0).diseaseId()).isEqualTo(a);
    }

    @Test
    void emptyKnowledgeKeepsVisionScoresHalved() {
        List<RankedCandidate> vision =
                List.of(new RankedCandidate(d1, new BigDecimal("0.80"), 1, "d1"));
        List<RankedCandidate> merged = MergeRanker.merge(vision, List.of(), Map.of(d1, "d1"), 5);
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).confidence()).isEqualByComparingTo("0.4000");
    }
}
