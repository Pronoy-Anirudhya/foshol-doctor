package com.rootcause.foshol.review.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisoryJpaRepository extends JpaRepository<AdvisoryEntity, UUID> {

    Optional<AdvisoryEntity> findFirstByCaseIdOrderByVersionDesc(UUID caseId);

    List<AdvisoryEntity> findByCaseIdOrderByVersionAsc(UUID caseId);

    boolean existsByCaseId(UUID caseId);
}
