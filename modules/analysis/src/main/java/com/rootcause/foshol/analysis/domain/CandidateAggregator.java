package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CandidateAggregator {

    public static final String MAX = "MAX";

    public List<MappedCandidate> maxWithinImage(List<MappedCandidate> candidates) {
        return maxByDisease(candidates);
    }

    public List<MappedCandidate> aggregateAcrossImages(
            List<List<MappedCandidate>> perImage, String aggregationMode, int candidateLimit) {
        List<MappedCandidate> flat = new ArrayList<>();
        for (List<MappedCandidate> image : perImage) {
            flat.addAll(image);
        }
        List<MappedCandidate> aggregated = maxByDisease(flat);
        if (!MAX.equals(aggregationMode)) {
            aggregated = maxByDisease(flat);
        }
        List<MappedCandidate> sorted = new ArrayList<>(aggregated);
        sorted.sort(Comparator.comparing(MappedCandidate::confidence)
                .reversed()
                .thenComparing(MappedCandidate::diseaseCode));
        if (sorted.size() > candidateLimit) {
            sorted = new ArrayList<>(sorted.subList(0, candidateLimit));
        }
        List<MappedCandidate> scaled = new ArrayList<>(sorted.size());
        for (MappedCandidate c : sorted) {
            scaled.add(new MappedCandidate(
                    c.diseaseId(), c.diseaseCode(), AnalysisScale.confidence(c.confidence())));
        }
        return List.copyOf(scaled);
    }

    private List<MappedCandidate> maxByDisease(List<MappedCandidate> candidates) {
        Map<UUID, MappedCandidate> best = new HashMap<>();
        for (MappedCandidate c : candidates) {
            MappedCandidate existing = best.get(c.diseaseId());
            if (existing == null || c.confidence().compareTo(existing.confidence()) > 0) {
                best.put(
                        c.diseaseId(),
                        new MappedCandidate(
                                c.diseaseId(),
                                c.diseaseCode(),
                                AnalysisScale.confidence(c.confidence())));
            }
        }
        return new ArrayList<>(best.values());
    }
}
