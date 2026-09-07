package com.rootcause.foshol.analysis.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseCandidateJpaRepository extends JpaRepository<CaseCandidateEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CaseCandidateEntity c where c.caseId = :caseId and c.source in ('MODEL','KB','MERGED')")
    void deleteGeneratedForCase(@Param("caseId") UUID caseId);

    List<CaseCandidateEntity> findByCaseIdAndSourceOrderByRankAsc(UUID caseId, String source);
}
