package com.rootcause.foshol.review.infrastructure.adapter;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.api.RemedyRefView;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import com.rootcause.foshol.review.domain.CaseRejection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rootcause.foshol.review.infrastructure.persistence.AdvisoryEntity;
import com.rootcause.foshol.review.infrastructure.persistence.AdvisoryJpaRepository;
import com.rootcause.foshol.review.infrastructure.persistence.AdvisoryMapper;
import com.rootcause.foshol.review.infrastructure.persistence.AdvisoryRemedyEntity;
import com.rootcause.foshol.review.infrastructure.persistence.AdvisoryRemedyJpaRepository;
import com.rootcause.foshol.review.infrastructure.persistence.CaseRejectionJpaRepository;

@Service
public class ReviewSubmissionApiAdapter implements ReviewSubmissionApi {

    private final AdvisoryJpaRepository advisories;
    private final AdvisoryRemedyJpaRepository advisoryRemedies;
    private final CaseRejectionJpaRepository rejections;
    private final KnowledgeQueryApi knowledge;
    private final OfficerLookupApi officers;

    public ReviewSubmissionApiAdapter(
            AdvisoryJpaRepository advisories,
            AdvisoryRemedyJpaRepository advisoryRemedies,
            CaseRejectionJpaRepository rejections,
            KnowledgeQueryApi knowledge,
            OfficerLookupApi officers) {
        this.advisories = advisories;
        this.advisoryRemedies = advisoryRemedies;
        this.rejections = rejections;
        this.knowledge = knowledge;
        this.officers = officers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdvisoryView> findPublishedAdvisory(UUID caseId) {
        return advisories.findFirstByCaseIdOrderByVersionDesc(caseId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvisoryView> findAdvisoryHistory(UUID caseId) {
        return advisories.findByCaseIdOrderByVersionAsc(caseId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RejectionView> findRejection(UUID caseId) {
        return rejections.findByCaseId(caseId).map(entity -> {
            CaseRejection domain = AdvisoryMapper.toDomain(entity);
            String officerName = officers.findById(domain.officerId()).map(o -> o.name()).orElse("");
            return new RejectionView(
                    domain.caseId(),
                    domain.officerId(),
                    officerName,
                    domain.reasonCode(),
                    domain.messageBn(),
                    domain.createdAt());
        });
    }

    private AdvisoryView toView(AdvisoryEntity entity) {
        List<AdvisoryRemedyEntity> rows = advisoryRemedies.findByAdvisoryIdOrderByDisplayOrderAsc(entity.getId());
        Map<UUID, RemedyView> byId = entity.getDiseaseId() == null
                ? Map.of()
                : knowledge.listActiveRemedies(entity.getDiseaseId()).stream()
                        .collect(Collectors.toMap(RemedyView::id, Function.identity(), (a, b) -> a));
        List<AdvisoryRemedy> remedies = new ArrayList<>();
        List<RemedyRefView> refs = new ArrayList<>();
        for (AdvisoryRemedyEntity row : rows) {
            remedies.add(new AdvisoryRemedy(row.getRemedyId(), row.getDisplayOrder()));
            RemedyView kb = byId.get(row.getRemedyId());
            refs.add(kb == null ? RemedyRefView.missing(row.getRemedyId()) : RemedyRefView.from(kb, null));
        }
        Advisory advisory = AdvisoryMapper.toDomain(entity, remedies);
        String officerName = officers.findById(advisory.officerId()).map(o -> o.name()).orElse("");
        Optional<DiseaseView> disease = entity.getDiseaseId() == null
                ? Optional.empty()
                : knowledge.findDiseaseById(entity.getDiseaseId());
        return AdvisoryView.of(
                advisory.id(),
                advisory.caseId(),
                advisory.diseaseId(),
                disease.map(DiseaseView::nameBn).orElse(null),
                disease.map(DiseaseView::nameEn).orElse(null),
                advisory.officerId(),
                officerName,
                advisory.action(),
                advisory.officerNoteBn(),
                advisory.version(),
                advisory.supersedesId(),
                refs,
                advisory.publishedAt());
    }
}
