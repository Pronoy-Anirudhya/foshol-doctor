package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.knowledge.infrastructure.persistence.DiseaseSymptomEntity.DiseaseSymptomId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseSymptomJpaRepository
        extends JpaRepository<DiseaseSymptomEntity, DiseaseSymptomId> {}
