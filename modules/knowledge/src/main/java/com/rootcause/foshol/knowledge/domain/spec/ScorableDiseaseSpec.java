package com.rootcause.foshol.knowledge.domain.spec;

import java.time.Instant;
import java.util.UUID;

public final class ScorableDiseaseSpec {

    private ScorableDiseaseSpec() {}

    public static boolean isSatisfiedBy(
            UUID cropId, UUID requestedCropId, Instant deletedAt, boolean healthy) {
        return requestedCropId != null
                && requestedCropId.equals(cropId)
                && LiveContentSpec.isLive(deletedAt)
                && !healthy;
    }
}
