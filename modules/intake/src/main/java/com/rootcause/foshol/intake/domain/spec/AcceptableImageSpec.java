package com.rootcause.foshol.intake.domain.spec;

import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import java.util.Set;

public final class AcceptableImageSpec {

    private final Set<String> allowedTypes;
    private final int maxBytes;
    private final int minEdgePx;

    public AcceptableImageSpec(Set<String> allowedTypes, int maxBytes, int minEdgePx) {
        this.allowedTypes = Set.copyOf(allowedTypes);
        this.maxBytes = maxBytes;
        this.minEdgePx = minEdgePx;
    }

    public boolean isSatisfied(String sniffedType, int byteSize, ImageMetrics metrics) {
        if (sniffedType == null || !allowedTypes.contains(sniffedType)) {
            return false;
        }
        if (byteSize > maxBytes) {
            return false;
        }
        return metrics != null && metrics.shorterEdge() >= minEdgePx;
    }
}
