package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.infrastructure.DiseaseSymptomEntity.DiseaseSymptomId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseSymptomJpaRepository
        extends JpaRepository<DiseaseSymptomEntity, DiseaseSymptomId> {}
