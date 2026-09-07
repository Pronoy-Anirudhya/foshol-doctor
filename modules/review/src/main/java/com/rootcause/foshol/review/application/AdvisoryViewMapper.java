package com.rootcause.foshol.review.application;

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
        String diseaseName =
                knowledge.findDiseaseById(advisory.diseaseId()).map(DiseaseView::nameBn).orElse("");
        String officerName = officers.findById(advisory.officerId()).map(o -> o.name()).orElse("");
        List<RemedyView> kb = knowledge.listActiveRemedies(advisory.diseaseId());
        List<RemedyRefView> ordered = new ArrayList<>();
        for (AdvisoryRemedy item : advisory.remedies()) {
            RemedyView match =
                    kb.stream().filter(r -> r.id().equals(item.remedyId())).findFirst().orElse(null);
            if (match != null) {
                ordered.add(toRef(match));
            } else {
                ordered.add(new RemedyRefView(item.remedyId(), null, "", List.of(), null, null, ""));
            }
        }
        return new AdvisoryView(
                advisory.id(),
                advisory.caseId(),
                advisory.diseaseId(),
                diseaseName,
                advisory.officerId(),
                officerName,
                advisory.action(),
                advisory.officerNoteBn(),
                advisory.version(),
                advisory.supersedesId(),
                ordered,
                advisory.publishedAt());
    }

    public static RemedyRefView toRef(RemedyView remedy) {
        return new RemedyRefView(
                remedy.id(),
                remedy.type(),
                remedy.titleBn(),
                remedy.stepsBn(),
                remedy.dosageBn(),
                remedy.phiDays(),
                remedy.sourceRef());
    }
}
