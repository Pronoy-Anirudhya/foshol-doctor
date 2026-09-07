package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.AdvisoryAction;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AdvisoryRepositoryAdapter implements AdvisoryRepository {

    private final AdvisoryJpaRepository jpa;
    private final AdvisoryRemedyJpaRepository remedies;

    public AdvisoryRepositoryAdapter(AdvisoryJpaRepository jpa, AdvisoryRemedyJpaRepository remedies) {
        this.jpa = jpa;
        this.remedies = remedies;
    }

    @Override
    public void insert(Advisory advisory) {
        jpa.saveAndFlush(AdvisoryMapper.toNewEntity(advisory));
        short order = 0;
        for (AdvisoryRemedy remedy : advisory.remedies()) {
            remedies.save(new AdvisoryRemedyEntity(advisory.id(), remedy.remedyId(), order++));
        }
    }

    @Override
    public Optional<Advisory> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Advisory> findPublishedByCaseId(UUID caseId) {
        return jpa.findFirstByCaseIdOrderByVersionDesc(caseId).map(this::toDomain);
    }

    @Override
    public List<Advisory> findHistoryByCaseId(UUID caseId) {
        return jpa.findByCaseIdOrderByVersionAsc(caseId).stream().map(this::toDomain).toList();
    }

    private Advisory toDomain(AdvisoryEntity entity) {
        List<AdvisoryRemedy> rows = remedies.findByAdvisoryIdOrderByDisplayOrderAsc(entity.getId()).stream()
                .map(r -> new AdvisoryRemedy(r.getRemedyId(), r.getDisplayOrder()))
                .toList();
        return new Advisory(
                entity.getId(),
                entity.getCaseId(),
                entity.getDiseaseId(),
                entity.getOfficerId(),
                AdvisoryAction.valueOf(entity.getAction()),
                entity.getOfficerNoteBn(),
                entity.getVersion(),
                entity.getSupersedesId(),
                rows,
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getCreatedBy());
    }
}
