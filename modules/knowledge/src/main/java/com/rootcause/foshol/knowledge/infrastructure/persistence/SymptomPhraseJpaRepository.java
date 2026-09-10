package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SymptomPhraseJpaRepository extends JpaRepository<SymptomPhraseEntity, UUID> {

    @Query(
            """
            select p from SymptomPhraseEntity p
            where p.deletedAt is null
            order by p.id asc
            """)
    List<SymptomPhraseEntity> findLivePhrases();

    @Query(
            value =
                    "select id from symptom_phrase where deleted_at is null and embedding is null order by id",
            nativeQuery = true)
    List<UUID> findLiveMissingEmbedding();
}
