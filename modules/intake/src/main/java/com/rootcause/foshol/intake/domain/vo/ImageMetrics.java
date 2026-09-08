package com.rootcause.foshol.intake.domain.vo;

public record ImageMetrics(
        int width, int height, double blurVariance, double exposureScore, double vegetationCoverage) {

    public int shorterEdge() {
        return Math.min(width, height);
    }

    public int longestEdge() {
        return Math.max(width, height);
    }
}
