package com.rootcause.foshol.knowledge.application.port;

import com.rootcause.foshol.knowledge.domain.SymptomRef;
import java.util.Optional;
import java.util.UUID;

public interface SymptomCatalog {

    Optional<SymptomRef> findLive(UUID symptomId);
}
