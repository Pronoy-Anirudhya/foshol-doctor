package com.rootcause.foshol.review.domain.spec;

import com.rootcause.foshol.knowledge.api.DiseaseView;
import java.util.List;

public final class AdvisoryHasRequiredRemedy {

    private final boolean healthy;

    public AdvisoryHasRequiredRemedy(DiseaseView disease) {
        this.healthy = disease.healthy();
    }

    public boolean isSatisfiedBy(List<?> remedies) {
        return healthy || !remedies.isEmpty();
    }
}
