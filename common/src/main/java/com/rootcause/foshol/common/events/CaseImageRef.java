package com.rootcause.foshol.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record CaseImageRef(
        UUID imageId,
        String objectKey,
        String derivativeObjectKey,
        String sha256,
        BigDecimal qualityScore,
        boolean primary,
        int position) {}
