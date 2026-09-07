package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.util.List;

public final class InconclusiveSpec {

    private InconclusiveSpec() {}

    public static boolean noSymptomsMatched(List<SymptomMatch> symptoms) {
        return symptoms == null || symptoms.isEmpty();
    }

    public static boolean rankOneBelowMinimum(List<DiseaseScore> diseases, BigDecimal minimum) {
        if (diseases == null || diseases.isEmpty()) {
            return true;
        }
        return diseases.getFirst().score().compareTo(minimum) < 0;
    }

    public static boolean isInconclusive(
            List<SymptomMatch> symptoms, List<DiseaseScore> diseases, BigDecimal minimum) {
        if (noSymptomsMatched(symptoms)) {
            return true;
        }
        return rankOneBelowMinimum(diseases, minimum);
    }
}
