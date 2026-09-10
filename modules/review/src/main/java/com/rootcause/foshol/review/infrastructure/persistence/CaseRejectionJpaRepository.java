package com.rootcause.foshol.review.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRejectionJpaRepository extends JpaRepository<CaseRejectionEntity, UUID> {

    Optional<CaseRejectionEntity> findByCaseId(UUID caseId);

    boolean existsByCaseId(UUID caseId);
}
