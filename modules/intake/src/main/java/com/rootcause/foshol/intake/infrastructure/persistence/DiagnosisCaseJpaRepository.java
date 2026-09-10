package com.rootcause.foshol.intake.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisCaseJpaRepository extends JpaRepository<DiagnosisCaseEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"images", "audio"})
    Optional<DiagnosisCaseEntity> findById(UUID id);

    long countByFarmerIdAndCreatedAtAfter(UUID farmerId, Instant createdAt);

    Optional<DiagnosisCaseEntity> findFirstByFarmerIdAndCreatedAtAfterOrderByCreatedAtAsc(
            UUID farmerId, Instant createdAt);
}
