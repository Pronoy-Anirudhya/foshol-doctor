package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MergeRanker {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private MergeRanker() {}

    public static List<RankedCandidate> merge(
            List<RankedCandidate> vision,
            List<ScoredDiseaseScore> knowledge,
            Map<UUID, String> diseaseCodes,
            int limit) {
        Map<UUID, BigDecimal> visionScores = new HashMap<>();
        if (vision != null) {
            for (RankedCandidate candidate : vision) {
                visionScores.put(candidate.diseaseId(), candidate.confidence());
            }
        }
        Map<UUID, BigDecimal> kbScores = new HashMap<>();
        if (knowledge != null) {
            for (ScoredDiseaseScore scored : knowledge) {
                kbScores.put(scored.diseaseId(), scored.score());
            }
        }
        Set<UUID> union = new HashSet<>();
        union.addAll(visionScores.keySet());
        union.addAll(kbScores.keySet());
        List<UUID> ids = new ArrayList<>(union);
        ids.sort((left, right) -> {
            BigDecimal mergedLeft = merged(visionScores, kbScores, left);
            BigDecimal mergedRight = merged(visionScores, kbScores, right);
            int byMerged = mergedRight.compareTo(mergedLeft);
            if (byMerged != 0) {
                return byMerged;
            }
            int byVision = zeroIfAbsent(visionScores, right).compareTo(zeroIfAbsent(visionScores, left));
            if (byVision != 0) {
                return byVision;
            }
            int byKb = zeroIfAbsent(kbScores, right).compareTo(zeroIfAbsent(kbScores, left));
            if (byKb != 0) {
                return byKb;
            }
            return codeOf(diseaseCodes, left).compareTo(codeOf(diseaseCodes, right));
        });
        if (limit >= 0 && ids.size() > limit) {
            ids = ids.subList(0, limit);
        }
        List<RankedCandidate> ranked = new ArrayList<>(ids.size());
        int rank = 1;
        for (UUID diseaseId : ids) {
            ranked.add(new RankedCandidate(
                    diseaseId,
                    merged(visionScores, kbScores, diseaseId),
                    rank,
                    diseaseCodes.get(diseaseId)));
            rank++;
        }
        return List.copyOf(ranked);
    }

    private static BigDecimal merged(
            Map<UUID, BigDecimal> visionScores, Map<UUID, BigDecimal> kbScores, UUID diseaseId) {
        return zeroIfAbsent(visionScores, diseaseId)
                .add(zeroIfAbsent(kbScores, diseaseId))
                .divide(TWO, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfAbsent(Map<UUID, BigDecimal> scores, UUID diseaseId) {
        BigDecimal value = scores.get(diseaseId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String codeOf(Map<UUID, String> diseaseCodes, UUID diseaseId) {
        String code = diseaseCodes == null ? null : diseaseCodes.get(diseaseId);
        return code == null ? diseaseId.toString() : code;
    }
}
