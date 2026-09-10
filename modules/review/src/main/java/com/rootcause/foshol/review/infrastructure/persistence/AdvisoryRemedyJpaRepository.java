package com.rootcause.foshol.review.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisoryRemedyJpaRepository extends JpaRepository<AdvisoryRemedyEntity, AdvisoryRemedyEntity.Key> {

    List<AdvisoryRemedyEntity> findByAdvisoryIdOrderByDisplayOrderAsc(UUID advisoryId);
}
