package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class FuzzyOverlap {

    private static final int SCALE = 10;

    private FuzzyOverlap() {}

    public static BigDecimal overlap(Set<String> phraseTokens, Set<String> transcriptTokens) {
        if (phraseTokens == null || phraseTokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (transcriptTokens == null || transcriptTokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int hits = 0;
        for (String token : phraseTokens) {
            if (transcriptTokens.contains(token)) {
                hits++;
            }
        }
        return BigDecimal.valueOf(hits)
                .divide(BigDecimal.valueOf(phraseTokens.size()), SCALE, RoundingMode.HALF_UP);
    }
}
