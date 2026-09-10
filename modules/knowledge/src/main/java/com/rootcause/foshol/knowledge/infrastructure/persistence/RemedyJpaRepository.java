package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RemedyJpaRepository extends JpaRepository<RemedyEntity, UUID> {

    @Query(
            """
            select r.id as id, r.diseaseId as diseaseId, r.type as type, r.titleBn as titleBn,
                   r.stepsBn as stepsBn, r.dosageBn as dosageBn, r.phiDays as phiDays,
                   r.costTier as costTier, r.efficacy as efficacy, r.sourceRef as sourceRef,
                   r.displayOrder as displayOrder, r.rateAmount as rateAmount, r.rateUnit as rateUnit,
                   r.rateBasis as rateBasis, r.rateNotesBn as rateNotesBn, r.titleEn as titleEn,
                   r.stepsEn as stepsEn, r.dosageEn as dosageEn, r.rateNotesEn as rateNotesEn
            from RemedyEntity r
            where r.diseaseId = :diseaseId and r.active = true and r.deletedAt is null
            order by r.displayOrder asc, r.id asc
            """)
    List<RemedyReadRow> findActiveByDiseaseId(UUID diseaseId);

    @Query("select count(r) from RemedyEntity r where r.deletedAt is null")
    long countLive();

    @Query(
            """
            select r.id from RemedyEntity r
            where r.deletedAt is null and trim(r.sourceRef) = ''
            order by r.id
            """)
    List<UUID> findLiveWithBlankSourceRef();
}
