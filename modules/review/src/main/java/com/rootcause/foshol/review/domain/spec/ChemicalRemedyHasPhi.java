package com.rootcause.foshol.review.domain.spec;

import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.knowledge.api.RemedyView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ChemicalRemedyHasPhi {

    private final Map<UUID, RemedyView> byId;

    public ChemicalRemedyHasPhi(List<RemedyView> remedies) {
        this.byId = remedies.stream().collect(Collectors.toMap(RemedyView::id, Function.identity()));
    }

    public boolean isSatisfiedBy(List<UUID> selectedIds) {
        return selectedIds.stream().allMatch(id -> {
            RemedyView remedy = byId.get(id);
            if (remedy == null) {
                return true;
            }
            return remedy.type() != RemedyType.CHEMICAL || remedy.phiDays() != null;
        });
    }
}
