package com.rootcause.foshol.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SymptomDeduplicatorTest {

    private static final UUID S = UUID.fromString("01800000-0000-7000-8000-000000000501");

    @Test
    void keepsMaximumScoreAndPrefersFuzzyWhenHigher() {
        List<SymptomMatch> hits = List.of(
                SymptomDeduplicator.vectorHit(S, "s1", "one", new BigDecimal("0.80")),
                SymptomDeduplicator.fuzzyHit(S, "s1", "one", new BigDecimal("0.90")));
        List<SymptomMatch> result = SymptomDeduplicator.deduplicateAutomatic(hits, 8);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().score()).isEqualByComparingTo("0.900");
        assertThat(result.getFirst().matcher()).isEqualTo(MatchLayer.FUZZY);
    }

    @Test
    void tieBreaksToVector() {
        List<SymptomMatch> hits = List.of(
                SymptomDeduplicator.fuzzyHit(S, "s1", "one", new BigDecimal("0.80")),
                SymptomDeduplicator.vectorHit(S, "s1", "one", new BigDecimal("0.80")));
        List<SymptomMatch> result = SymptomDeduplicator.deduplicateAutomatic(hits, 8);
        assertThat(result.getFirst().matcher()).isEqualTo(MatchLayer.VECTOR);
    }

    @Test
    void capsByScoreThenCode() {
        UUID a = UUID.fromString("01800000-0000-7000-8000-000000000510");
        UUID b = UUID.fromString("01800000-0000-7000-8000-000000000511");
        List<SymptomMatch> hits = List.of(
                SymptomDeduplicator.vectorHit(a, "b", "b", new BigDecimal("0.90")),
                SymptomDeduplicator.vectorHit(b, "a", "a", new BigDecimal("0.90")));
        List<SymptomMatch> result = SymptomDeduplicator.deduplicateAutomatic(hits, 1);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("a");
    }

    @Test
    void roundsHalfUpToThreePlaces() {
        SymptomMatch down = SymptomDeduplicator.vectorHit(S, "s1", "one", new BigDecimal("0.72349"));
        SymptomMatch up = SymptomDeduplicator.vectorHit(S, "s1", "one", new BigDecimal("0.72350"));
        assertThat(down.score()).isEqualByComparingTo("0.723");
        assertThat(up.score()).isEqualByComparingTo("0.724");
    }

    @Test
    void officerMatchReplacesAutomatic() {
        List<SymptomMatch> automatic =
                List.of(SymptomDeduplicator.vectorHit(S, "s1", "one", new BigDecimal("0.80")));
        List<SymptomMatch> officer =
                List.of(new SymptomMatch(S, "s1", "one", BigDecimal.ONE, MatchLayer.MANUAL));
        List<SymptomMatch> merged = SymptomDeduplicator.admitOfficer(automatic, officer);
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().matcher()).isEqualTo(MatchLayer.MANUAL);
        assertThat(merged.getFirst().score()).isEqualByComparingTo("1.000");
    }
}
