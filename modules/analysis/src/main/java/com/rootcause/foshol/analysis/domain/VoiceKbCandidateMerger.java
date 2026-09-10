package com.rootcause.foshol.analysis.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoiceKbCandidateMerger {

    private VoiceKbCandidateMerger() {}

    public static List<VoiceKbCandidate> merge(
            List<DiseaseNameHit> nameHits, List<VoiceKbCandidate> symptomHits, int limit) {
        Map<UUID, VoiceKbCandidate> byId = new LinkedHashMap<>();
        if (nameHits != null) {
            for (DiseaseNameHit hit : nameHits) {
                putIfBetter(
                        byId,
                        new VoiceKbCandidate(
                                hit.diseaseId(),
                                hit.code(),
                                hit.nameBn(),
                                hit.nameEn(),
                                AnalysisScale.score(hit.score()),
                                VoiceKbMatchers.NAME));
            }
        }
        if (symptomHits != null) {
            for (VoiceKbCandidate hit : symptomHits) {
                putIfBetter(byId, hit);
            }
        }
        List<VoiceKbCandidate> ranked = new ArrayList<>(byId.values());
        ranked.sort(Comparator.comparing(VoiceKbCandidate::score)
                .reversed()
                .thenComparing(VoiceKbCandidate::code));
        int cap = Math.max(limit, 0);
        if (ranked.size() > cap) {
            return List.copyOf(ranked.subList(0, cap));
        }
        return List.copyOf(ranked);
    }

    private static void putIfBetter(Map<UUID, VoiceKbCandidate> byId, VoiceKbCandidate hit) {
        VoiceKbCandidate existing = byId.get(hit.diseaseId());
        if (existing == null || better(hit, existing)) {
            byId.put(hit.diseaseId(), hit);
        }
    }

    private static boolean better(VoiceKbCandidate candidate, VoiceKbCandidate existing) {
        int compared = candidate.score().compareTo(existing.score());
        if (compared > 0) {
            return true;
        }
        if (compared < 0) {
            return false;
        }
        if (VoiceKbMatchers.NAME.equals(candidate.matcher()) && !VoiceKbMatchers.NAME.equals(existing.matcher())) {
            return true;
        }
        return candidate.code().compareTo(existing.code()) < 0;
    }
}
