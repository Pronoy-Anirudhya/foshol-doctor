package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SymptomJpaRepository extends JpaRepository<SymptomEntity, UUID> {

    @Query(
            """
            select s.id as id, s.code as code, s.nameBn as nameBn, s.nameEn as nameEn, s.organ as organ
            from SymptomEntity s
            where s.deletedAt is null
            order by s.code asc
            """)
    List<SymptomReadRow> findLiveSymptoms();

    @Query(
            """
            select s.id as id, s.code as code, s.nameBn as nameBn, s.nameEn as nameEn, s.organ as organ
            from SymptomEntity s
            where s.id = :id and s.deletedAt is null
            """)
    Optional<SymptomReadRow> findLiveById(UUID id);
}
