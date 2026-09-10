package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiseaseNameMatcherTest {

    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID HEALTHY = UUID.fromString("01800000-0000-7000-8000-000000000106");
    static final UUID POTATO_EARLY = UUID.fromString("01800000-0000-7000-8000-000000000112");
    static final UUID POTATO_LATE = UUID.fromString("01800000-0000-7000-8000-000000000113");

    @Test
    void matchesEnglishCodeInsideTranscript() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match(
                "I think it is blast",
                List.of(blast(), healthy()));
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().diseaseId()).isEqualTo(BLAST);
        assertThat(hits.getFirst().score()).isGreaterThanOrEqualTo(VoiceKbMatchers.NAME_OVERLAP_MIN);
        assertThat(hits.getFirst().nameEn()).isEqualTo("Blast");
    }

    @Test
    void matchesBanglaName() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match("আমার ধানে ব্লাস্ট হয়েছে", List.of(blast(), healthy()));
        assertThat(hits).extracting(DiseaseNameHit::diseaseId).containsExactly(BLAST);
    }

    @Test
    void matchesMisheardPotatoEarlyBlight() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match(
                "হালুর, আগাম, ধশা।",
                List.of(potatoEarly(), potatoLate(), potatoHealthy()));
        assertThat(hits).extracting(DiseaseNameHit::diseaseId).containsExactly(POTATO_EARLY);
        assertThat(hits.getFirst().nameBn()).isEqualTo("আলুর আগাম ধ্বসা");
        assertThat(hits.getFirst().nameEn()).isEqualTo("Early blight");
    }

    @Test
    void doesNotMatchLateBlightForEarlyBlightUtterance() {
        List<DiseaseNameHit> hits = DiseaseNameMatcher.match("হালুর, আগাম, ধশা।", List.of(potatoLate()));
        assertThat(hits).extracting(DiseaseNameHit::diseaseId).doesNotContain(POTATO_LATE);
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

    private static DiseaseNameRef potatoEarly() {
        return new DiseaseNameRef(POTATO_EARLY, "early_blight", "আলুর আগাম ধ্বসা", "Early blight", false);
    }

    private static DiseaseNameRef potatoLate() {
        return new DiseaseNameRef(POTATO_LATE, "late_blight", "আলুর নাবী ধ্বসা", "Late blight", false);
    }

    private static DiseaseNameRef potatoHealthy() {
        return new DiseaseNameRef(
                UUID.fromString("01800000-0000-7000-8000-000000000114"),
                "healthy",
                "সুস্থ আলু",
                "Healthy",
                true);
    }
}
