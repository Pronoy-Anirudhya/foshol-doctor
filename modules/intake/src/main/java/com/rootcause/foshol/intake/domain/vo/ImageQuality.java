package com.rootcause.foshol.intake.domain.vo;

import java.math.BigDecimal;

public record ImageQuality(
        double blurVariance,
        double exposureScore,
        BigDecimal qualityScore,
        int width,
        int height) {

    public int shorterEdge() {
        return Math.min(width, height);
    }

    public int longestEdge() {
        return Math.max(width, height);
    }
}
