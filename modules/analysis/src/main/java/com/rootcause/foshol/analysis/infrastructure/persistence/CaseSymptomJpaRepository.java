package com.rootcause.foshol.analysis.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseSymptomJpaRepository extends JpaRepository<CaseSymptomEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CaseSymptomEntity s where s.caseId = :caseId and s.source = 'SPEECH'")
    void deleteSpeechForCase(@Param("caseId") UUID caseId);

    boolean existsByCaseIdAndSymptomIdAndSource(UUID caseId, UUID symptomId, String source);

    List<CaseSymptomEntity> findByCaseIdOrderBySourceAscScoreDesc(UUID caseId);
}
