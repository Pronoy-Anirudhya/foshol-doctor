package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record DiseaseSymptomWeight(UUID diseaseId, UUID symptomId, BigDecimal weight) {

    public DiseaseSymptomWeight {
        if (diseaseId == null || symptomId == null) {
            throw new IllegalArgumentException("diseaseId and symptomId are required");
        }
        if (weight == null
                || weight.compareTo(BigDecimal.ZERO) <= 0
                || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("weight must lie in (0, 1]");
        }
    }
}
