package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LabelResolver {

    private static final Logger log = LoggerFactory.getLogger(LabelResolver.class);

    @FunctionalInterface
    public interface ModelLabelLookup {
        Optional<UUID> resolve(String modelId, String modelVersion, String rawLabel);
    }

    @FunctionalInterface
    public interface DiseaseCodeLookup {
        String codeOf(UUID diseaseId);
    }

    public record Resolution(List<MappedCandidate> mapped, List<String> unmappedLabels) {}

    public Resolution resolve(
            List<RawCandidate> raw,
            String modelId,
            String modelVersion,
            ModelLabelLookup lookup,
            DiseaseCodeLookup codes,
            String correlationId) {
        List<MappedCandidate> mapped = new ArrayList<>();
        Set<String> unmapped = new LinkedHashSet<>();
        for (RawCandidate candidate : raw) {
            Optional<UUID> diseaseId = lookup.resolve(modelId, modelVersion, candidate.rawLabel());
            if (diseaseId.isEmpty()) {
                if (unmapped.add(candidate.rawLabel())) {
                    log.warn(
                            "Unmapped model label {} for model {} {} correlationId={}",
                            candidate.rawLabel(),
                            modelId,
                            modelVersion,
                            correlationId);
                }
                continue;
            }
            mapped.add(new MappedCandidate(
                    diseaseId.get(),
                    codes.codeOf(diseaseId.get()),
                    AnalysisScale.confidence(candidate.confidence())));
        }
        return new Resolution(List.copyOf(mapped), List.copyOf(unmapped));
    }
}
