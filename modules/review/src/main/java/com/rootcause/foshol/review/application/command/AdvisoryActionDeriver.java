package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.knowledge.api.RemedyView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdvisoryActionDeriver {

    private AdvisoryActionDeriver() {}

    public static AdvisoryAction derive(
            UUID submittedDiseaseId,
            List<UUID> submittedRemedyIds,
            String officerNoteBn,
            List<CandidateView> candidates,
            List<RemedyView> kbRemedies) {
        UUID rank1Model = rank1ModelDisease(candidates);
        if (rank1Model == null || !rank1Model.equals(submittedDiseaseId)) {
            return AdvisoryAction.REPLACED;
        }
        Set<UUID> submitted = new HashSet<>(submittedRemedyIds);
        Set<UUID> kb = new HashSet<>();
        for (RemedyView remedy : kbRemedies) {
            kb.add(remedy.id());
        }
        boolean notePresent = officerNoteBn != null && !officerNoteBn.isBlank();
        if (!submitted.equals(kb) || notePresent) {
            return AdvisoryAction.EDITED;
        }
        return AdvisoryAction.APPROVED;
    }

    static UUID rank1ModelDisease(List<CandidateView> candidates) {
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(c -> c.source() == CandidateSource.MODEL && c.rank() == 1)
                .map(CandidateView::diseaseId)
                .findFirst()
                .orElse(null);
    }
}
