package com.rootcause.foshol.analysis.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, UUID> {

    boolean existsByCaseIdAndDecisionPathIsNotNull(UUID caseId);

    Optional<AnalysisRunEntity> findFirstByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
