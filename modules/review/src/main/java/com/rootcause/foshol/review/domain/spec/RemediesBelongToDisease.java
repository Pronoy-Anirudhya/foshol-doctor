package com.rootcause.foshol.review.domain.spec;

import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RemediesBelongToDisease {

    private final Set<UUID> allowed;

    public RemediesBelongToDisease(List<RemedyView> activeForDisease) {
        this.allowed = activeForDisease.stream().map(RemedyView::id).collect(Collectors.toSet());
    }

    public boolean isSatisfiedBy(List<AdvisoryRemedy> remedies) {
        return remedies.stream().allMatch(r -> allowed.contains(r.remedyId()));
    }
}
