package com.rootcause.foshol.review.application.command;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.knowledge.api.RemedyView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdvisoryActionResolver {

    private AdvisoryActionResolver() {}

    public static AdvisoryAction resolve(
            UUID submittedDiseaseId,
            List<UUID> submittedRemedyIds,
            String officerNoteBn,
            List<CandidateView> candidates,
            List<RemedyView> kbRemediesForSubmittedDisease) {
        CandidateView topModel = candidates.stream()
                .filter(c -> c.source() == CandidateSource.MODEL && c.rank() == 1)
                .findFirst()
                .orElse(null);
        if (topModel == null || !topModel.diseaseId().equals(submittedDiseaseId)) {
            return AdvisoryAction.REPLACED;
        }
        Set<UUID> kbIds = new HashSet<>();
        for (RemedyView remedy : kbRemediesForSubmittedDisease) {
            kbIds.add(remedy.id());
        }
        Set<UUID> submitted = new HashSet<>(submittedRemedyIds);
        boolean remediesMatch = kbIds.equals(submitted);
        boolean hasNote = officerNoteBn != null && !officerNoteBn.isBlank();
        if (remediesMatch && !hasNote) {
            return AdvisoryAction.APPROVED;
        }
        return AdvisoryAction.EDITED;
    }
}
