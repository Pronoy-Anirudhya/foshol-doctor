package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RemedyRefView;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import java.util.ArrayList;
import java.util.List;

public final class AdvisoryViewMapper {

    private AdvisoryViewMapper() {}

    public static AdvisoryView toView(
            Advisory advisory, KnowledgeQueryApi knowledge, OfficerLookupApi officers) {
        DiseaseView disease = knowledge.findDiseaseById(advisory.diseaseId()).orElse(null);
        String diseaseNameBn = disease == null ? "" : disease.nameBn();
        String diseaseNameEn = disease == null ? null : disease.nameEn();
        String officerName = officers.findById(advisory.officerId()).map(o -> o.name()).orElse("");
        List<RemedyView> kb = knowledge.listActiveRemedies(advisory.diseaseId());
        List<RemedyRefView> ordered = new ArrayList<>();
        for (AdvisoryRemedy item : advisory.remedies()) {
            RemedyView match =
                    kb.stream().filter(r -> r.id().equals(item.remedyId())).findFirst().orElse(null);
            if (match != null) {
                ordered.add(RemedyRefView.from(match, null));
            } else {
                ordered.add(RemedyRefView.missing(item.remedyId()));
            }
        }
        return AdvisoryView.of(
                advisory.id(),
                advisory.caseId(),
                advisory.diseaseId(),
                diseaseNameBn,
                diseaseNameEn,
                advisory.officerId(),
                officerName,
                advisory.action(),
                advisory.officerNoteBn(),
                advisory.version(),
                advisory.supersedesId(),
                ordered,
                advisory.publishedAt());
    }
}