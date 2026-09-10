package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiseaseNameMatcherTest {

    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID HEALTHY = UUID.fromString("01800000-0000-7000-8000-000000000106");

    @Test
    void matchesEnglishCodeInsideTranscript() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match(
                "I think it is blast",
                List.of(blast(), healthy()));
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().diseaseId()).isEqualTo(BLAST);
        assertThat(hits.getFirst().score()).isGreaterThanOrEqualTo(VoiceKbMatchers.NAME_OVERLAP_MIN);
    }

    @Test
    void matchesBanglaName() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match("আমার ধানে ব্লাস্ট হয়েছে", List.of(blast(), healthy()));
        assertThat(hits).extracting(DiseaseNameHit::diseaseId).containsExactly(BLAST);
    }

    @Test
    void skipsHealthyDiseases() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match("healthy", List.of(healthy()));
        assertThat(hits).isEmpty();
    }

    @Test
    void emptyTranscriptYieldsNoHits() {
        assertThat(DiseaseNameMatcher.match("   ", List.of(blast()))).isEmpty();
    }

    private static DiseaseNameRef blast() {
        return new DiseaseNameRef(BLAST, "BLAST", "ব্লাস্ট", "Blast", false);
    }

    private static DiseaseNameRef healthy() {
        return new DiseaseNameRef(HEALTHY, "HEALTHY", "সুস্থ", "Healthy", true);
    }
}
