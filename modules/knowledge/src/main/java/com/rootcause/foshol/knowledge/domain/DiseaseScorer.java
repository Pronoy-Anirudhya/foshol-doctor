package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DiseaseScorer {

    private DiseaseScorer() {}

    public static List<DiseaseScore> score(
            Map<UUID, BigDecimal> matchedScores, List<ScorableDiseaseSheet> diseases, UUID cropId) {
        List<DiseaseScore> scored = new ArrayList<>();
        for (ScorableDiseaseSheet disease : diseases) {
            if (!disease.scorableFor(cropId)) {
                continue;
            }
            BigDecimal totalWeight = BigDecimal.ZERO;
            BigDecimal weighted = BigDecimal.ZERO;
            for (DiseaseSymptomWeight weight : disease.weights()) {
                totalWeight = totalWeight.add(weight.weight());
                BigDecimal matchScore = matchedScores.get(weight.symptomId());
                if (matchScore != null) {
                    weighted = weighted.add(weight.weight().multiply(matchScore));
                }
            }
            if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if (weighted.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal raw = weighted.divide(totalWeight, 10, RoundingMode.HALF_UP);
            scored.add(new DiseaseScore(disease.diseaseId(), disease.code(), disease.nameBn(), raw, 1));
        }
        scored.sort(Comparator.comparing(DiseaseScore::score)
                .reversed()
                .thenComparing(DiseaseScore::code));
        List<DiseaseScore> ranked = new ArrayList<>(scored.size());
        int rank = 1;
        for (DiseaseScore row : scored) {
            ranked.add(new DiseaseScore(row.diseaseId(), row.code(), row.nameBn(), row.score(), rank++));
        }
        return List.copyOf(ranked);
    }
}
