package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoiceKbCandidateMergerTest {

    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID BROWN = UUID.fromString("01800000-0000-7000-8000-000000000101");

    @Test
    void prefersHigherScoreAndCapsResults() {
        List<VoiceKbCandidate> merged = VoiceKbCandidateMerger.merge(
                List.of(new DiseaseNameHit(BLAST, "BLAST", "ব্লাস্ট", new BigDecimal("0.800"))),
                List.of(
                        new VoiceKbCandidate(
                                BROWN, "BROWN_SPOT", "বাদামি দাগ", new BigDecimal("0.900"), VoiceKbMatchers.VECTOR),
                        new VoiceKbCandidate(
                                BLAST, "BLAST", "ব্লাস্ট", new BigDecimal("0.500"), VoiceKbMatchers.FUZZY)),
                1);
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().diseaseId()).isEqualTo(BROWN);
        assertThat(merged.getFirst().matcher()).isEqualTo(VoiceKbMatchers.VECTOR);
    }

    @Test
    void nameLayerWinsTies() {
        List<VoiceKbCandidate> merged = VoiceKbCandidateMerger.merge(
                List.of(new DiseaseNameHit(BLAST, "BLAST", "ব্লাস্ট", new BigDecimal("0.900"))),
                List.of(new VoiceKbCandidate(
                        BLAST, "BLAST", "ব্লাস্ট", new BigDecimal("0.900"), VoiceKbMatchers.VECTOR)),
                5);
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().matcher()).isEqualTo(VoiceKbMatchers.NAME);
    }
}
