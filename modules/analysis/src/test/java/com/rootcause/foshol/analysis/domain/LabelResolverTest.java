package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabelResolverTest {

    private final LabelResolver resolver = new LabelResolver();
    private final UUID disease = UUID.fromString("01800000-0000-7000-8000-000000000101");

    @Test
    void usesReportedModelVersion() {
        List<RawCandidate> raw = List.of(new RawCandidate("Brown Spot", new BigDecimal("0.90")));
        LabelResolver.Resolution resolution = resolver.resolve(
                raw,
                "model",
                "v2",
                (modelId, modelVersion, label) -> {
                    assertThat(modelVersion).isEqualTo("v2");
                    return Optional.of(disease);
                },
                id -> "brown_spot",
                "corr");
        assertThat(resolution.mapped()).hasSize(1);
        assertThat(resolution.unmappedLabels()).isEmpty();
    }

    @Test
    void appendsUnmappedWithoutDuplicates() {
        List<RawCandidate> raw = List.of(
                new RawCandidate("unknown", new BigDecimal("0.50")),
                new RawCandidate("unknown", new BigDecimal("0.40")),
                new RawCandidate("Brown Spot", new BigDecimal("0.30")));
        LabelResolver.Resolution resolution = resolver.resolve(
                raw,
                "model",
                "v1",
                (modelId, modelVersion, label) ->
                        "Brown Spot".equals(label) ? Optional.of(disease) : Optional.empty(),
                id -> "brown_spot",
                "corr");
        assertThat(resolution.mapped()).hasSize(1);
        assertThat(resolution.unmappedLabels()).containsExactly("unknown");
    }
}
