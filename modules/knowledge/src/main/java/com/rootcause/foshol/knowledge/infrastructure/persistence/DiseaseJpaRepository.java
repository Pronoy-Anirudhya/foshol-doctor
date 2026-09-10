package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiseaseJpaRepository extends JpaRepository<DiseaseEntity, UUID> {

    @Query("select count(d) from DiseaseEntity d where d.deletedAt is null")
    long countLive();

    @Query(
            """
            select d.id as id, d.cropId as cropId, d.code as code, d.nameBn as nameBn,
                   d.nameEn as nameEn, d.descriptionBn as descriptionBn, d.severity as severity,
                   d.healthy as healthy
            from DiseaseEntity d
            where d.id = :id and d.deletedAt is null
            """)
    Optional<DiseaseReadRow> findLiveById(UUID id);

    @Query(
            """
            select d.id as id, d.cropId as cropId, d.code as code, d.nameBn as nameBn,
                   d.nameEn as nameEn, d.descriptionBn as descriptionBn, d.severity as severity,
                   d.healthy as healthy
            from DiseaseEntity d
            where d.cropId = :cropId and d.deletedAt is null
            order by d.healthy asc, d.code asc
            """)
    List<DiseaseReadRow> findLiveByCropId(UUID cropId);

    @Query(
            """
            select d.id from DiseaseEntity d
            where d.deletedAt is null
              and d.healthy = false
              and not exists (
                  select 1 from RemedyEntity r
                  where r.diseaseId = d.id and r.active = true and r.deletedAt is null)
            order by d.id
            """)
    List<UUID> findNonHealthyMissingActiveRemedy();

    @Query(
            """
            select d.id from DiseaseEntity d
            where d.deletedAt is null
              and d.healthy = false
              and not exists (select 1 from DiseaseSymptomEntity ds where ds.diseaseId = d.id)
            order by d.id
            """)
    List<UUID> findNonHealthyMissingWeights();
}
