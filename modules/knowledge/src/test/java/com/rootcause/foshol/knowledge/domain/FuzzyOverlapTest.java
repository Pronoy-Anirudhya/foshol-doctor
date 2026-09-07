package com.rootcause.foshol.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FuzzyOverlapTest {

    @Test
    void overlapIsPhraseCoverage() {
        Set<String> phrase = Set.of("a", "b", "c");
        Set<String> transcript = Set.of("a", "c", "x");
        assertThat(FuzzyOverlap.overlap(phrase, transcript))
                .isEqualByComparingTo(new BigDecimal("2").divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP));
    }

    @Test
    void emptyPhraseIsZero() {
        assertThat(FuzzyOverlap.overlap(Set.of(), Set.of("a"))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void emptyTranscriptIsZero() {
        assertThat(FuzzyOverlap.overlap(Set.of("a"), Set.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
