package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AnalysisScale {

    public static final int CONFIDENCE = 4;
    public static final int SCORE = 3;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final int EMBEDDING_DIMENSION = 768;

    private AnalysisScale() {}

    public static BigDecimal confidence(BigDecimal value) {
        return value.setScale(CONFIDENCE, ROUNDING);
    }

    public static BigDecimal score(BigDecimal value) {
        return value.setScale(SCORE, ROUNDING);
    }
}
