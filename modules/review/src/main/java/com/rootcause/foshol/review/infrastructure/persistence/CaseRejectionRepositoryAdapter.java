package com.rootcause.foshol.review.infrastructure.persistence;

import com.rootcause.foshol.common.enums.RejectionReason;
import com.rootcause.foshol.review.application.port.CaseRejectionRepository;
import com.rootcause.foshol.review.domain.CaseRejection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRejectionRepositoryAdapter implements CaseRejectionRepository {

    private final CaseRejectionJpaRepository jpa;

    public CaseRejectionRepositoryAdapter(CaseRejectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(CaseRejection rejection) {
        jpa.saveAndFlush(AdvisoryMapper.toNewEntity(rejection));
    }

    @Override
    public Optional<CaseRejection> findByCaseId(UUID caseId) {
        return jpa.findByCaseId(caseId).map(AdvisoryMapper::toDomain);
    }
}
