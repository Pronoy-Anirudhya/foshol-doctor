package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.api.DiseaseView;
import java.util.List;

public record ListDiseasesByCropResult(boolean cropFound, List<DiseaseView> diseases) {}
