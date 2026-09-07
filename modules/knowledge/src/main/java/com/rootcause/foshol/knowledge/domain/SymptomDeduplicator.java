package com.rootcause.foshol.knowledge.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SymptomDeduplicator {

    private SymptomDeduplicator() {}

    public static List<SymptomMatch> deduplicateAutomatic(List<SymptomMatch> hits, int maxSymptoms) {
        Map<UUID, SymptomMatch> best = new HashMap<>();
        for (SymptomMatch hit : hits) {
            if (hit.matcher() == MatchLayer.MANUAL) {
                continue;
            }
            SymptomMatch existing = best.get(hit.symptomId());
            if (existing == null || isBetter(hit, existing)) {
                best.put(hit.symptomId(), hit);
            }
        }
        List<SymptomMatch> ordered = new ArrayList<>(best.values());
        ordered.sort(Comparator.comparing(SymptomMatch::score)
                .reversed()
                .thenComparing(SymptomMatch::code));
        if (ordered.size() > maxSymptoms) {
            return List.copyOf(ordered.subList(0, maxSymptoms));
        }
        return List.copyOf(ordered);
    }

    public static List<SymptomMatch> admitOfficer(
            List<SymptomMatch> automatic, List<SymptomMatch> officerMatches) {
        Map<UUID, SymptomMatch> merged = new HashMap<>();
        for (SymptomMatch match : automatic) {
            merged.put(match.symptomId(), match);
        }
        for (SymptomMatch officer : officerMatches) {
            merged.put(officer.symptomId(), officer);
        }
        List<SymptomMatch> automaticKept = new ArrayList<>();
        List<SymptomMatch> manualKept = new ArrayList<>();
        for (SymptomMatch match : merged.values()) {
            if (match.matcher() == MatchLayer.MANUAL) {
                manualKept.add(match);
            } else {
                automaticKept.add(match);
            }
        }
        automaticKept.sort(Comparator.comparing(SymptomMatch::score)
                .reversed()
                .thenComparing(SymptomMatch::code));
        manualKept.sort(Comparator.comparing(SymptomMatch::code));
        List<SymptomMatch> result = new ArrayList<>(automaticKept.size() + manualKept.size());
        result.addAll(automaticKept);
        result.addAll(manualKept);
        result.sort(Comparator.comparing(SymptomMatch::score)
                .reversed()
                .thenComparing(SymptomMatch::code));
        return List.copyOf(result);
    }

    private static boolean isBetter(SymptomMatch candidate, SymptomMatch current) {
        int cmp = candidate.score().compareTo(current.score());
        if (cmp > 0) {
            return true;
        }
        if (cmp < 0) {
            return false;
        }
        if (candidate.matcher() == MatchLayer.VECTOR && current.matcher() != MatchLayer.VECTOR) {
            return true;
        }
        return false;
    }

    public static SymptomMatch vectorHit(UUID symptomId, String code, String nameBn, BigDecimal similarity) {
        return new SymptomMatch(symptomId, code, nameBn, similarity, MatchLayer.VECTOR);
    }

    public static SymptomMatch fuzzyHit(UUID symptomId, String code, String nameBn, BigDecimal overlap) {
        return new SymptomMatch(symptomId, code, nameBn, overlap, MatchLayer.FUZZY);
    }
}
