package com.rootcause.foshol.knowledge.domain;

import java.util.List;
import java.util.UUID;

public record ScorableDiseaseSheet(
        UUID diseaseId,
        String code,
        String nameBn,
        UUID cropId,
        boolean healthy,
        boolean deleted,
        List<DiseaseSymptomWeight> weights) {

    public ScorableDiseaseSheet {
        weights = weights == null ? List.of() : List.copyOf(weights);
    }

    public boolean scorableFor(UUID requestedCropId) {
        return !deleted && !healthy && cropId.equals(requestedCropId);
    }
}
