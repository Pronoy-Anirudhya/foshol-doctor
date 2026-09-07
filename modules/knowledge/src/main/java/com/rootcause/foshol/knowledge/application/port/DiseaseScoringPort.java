package com.rootcause.foshol.knowledge.application.port;

import com.rootcause.foshol.knowledge.domain.DiseaseScore;
import com.rootcause.foshol.knowledge.domain.SymptomMatch;
import java.util.List;
import java.util.UUID;

public interface DiseaseScoringPort {

    List<DiseaseScore> score(UUID cropId, List<SymptomMatch> matches);
}
