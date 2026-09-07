package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record DiseaseScore(UUID diseaseId, String code, String nameBn, BigDecimal score, int rank) {

    public static final int SCORE_SCALE = 4;

    public DiseaseScore {
        if (diseaseId == null) {
            throw new IllegalArgumentException("diseaseId is required");
        }
        if (score == null) {
            throw new IllegalArgumentException("score is required");
        }
        BigDecimal rounded = score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ZERO) < 0 || rounded.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("disease score must lie in [0, 1]");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        score = rounded;
        code = code == null ? "" : code;
        nameBn = nameBn == null ? "" : nameBn;
    }
}
