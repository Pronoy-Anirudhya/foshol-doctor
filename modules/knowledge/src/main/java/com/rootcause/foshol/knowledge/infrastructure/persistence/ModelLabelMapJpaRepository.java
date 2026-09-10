package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelLabelMapJpaRepository extends JpaRepository<ModelLabelMapEntity, UUID> {

    @Query("select distinct m.modelId from ModelLabelMapEntity m")
    List<String> findDistinctModelIds();

    @Query(
            """
            select distinct m.diseaseId from ModelLabelMapEntity m
            where not exists (
                select 1 from DiseaseEntity d where d.id = m.diseaseId and d.deletedAt is null)
            """)
    List<UUID> findUnresolvedDiseaseIds();
}
