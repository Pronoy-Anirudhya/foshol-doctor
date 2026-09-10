package com.rootcause.foshol.analysis.domain.spec;

public final class PrescribableSpec {

    private PrescribableSpec() {}

    public static boolean isSatisfied(boolean healthy, boolean hasActiveRemedies) {
        return healthy || hasActiveRemedies;
    }
}
