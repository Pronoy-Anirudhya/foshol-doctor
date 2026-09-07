package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record SymptomMatch(UUID symptomId, String code, String nameBn, BigDecimal score, MatchLayer matcher) {

    public static final int SCORE_SCALE = 3;

    public SymptomMatch {
        if (symptomId == null) {
            throw new IllegalArgumentException("symptomId is required");
        }
        if (matcher == null) {
            throw new IllegalArgumentException("matcher is required");
        }
        if (score == null) {
            throw new IllegalArgumentException("score is required");
        }
        BigDecimal rounded = score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ZERO) <= 0 || rounded.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("symptom match score must lie in (0, 1]");
        }
        score = rounded;
        code = code == null ? "" : code;
        nameBn = nameBn == null ? "" : nameBn;
    }
}
