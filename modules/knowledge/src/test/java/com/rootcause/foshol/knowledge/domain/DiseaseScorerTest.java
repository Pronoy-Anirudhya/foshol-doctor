package com.rootcause.foshol.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiseaseScorerTest {

    private static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    private static final UUID OTHER_CROP = UUID.fromString("01800000-0000-7000-8000-000000000002");
    private static final UUID D = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private static final UUID S1 = UUID.fromString("01800000-0000-7000-8000-000000000501");
    private static final UUID S2 = UUID.fromString("01800000-0000-7000-8000-000000000502");

    @Test
    void normalisesByTotalWeight() {
        ScorableDiseaseSheet sheet = new ScorableDiseaseSheet(
                D,
                "blast",
                "Blast",
                CROP,
                false,
                false,
                List.of(
                        new DiseaseSymptomWeight(D, S1, new BigDecimal("0.8")),
                        new DiseaseSymptomWeight(D, S2, new BigDecimal("0.4"))));
        List<DiseaseScore> scores = DiseaseScorer.score(Map.of(S1, BigDecimal.ONE), List.of(sheet), CROP);
        assertThat(scores).hasSize(1);
        assertThat(scores.getFirst().score()).isEqualByComparingTo("0.6667");
        assertThat(scores.getFirst().rank()).isEqualTo(1);
    }

    @Test
    void excludesOtherCropsHealthyAndUnmatched() {
        UUID otherDisease = UUID.fromString("01800000-0000-7000-8000-000000000107");
        ScorableDiseaseSheet otherCrop = new ScorableDiseaseSheet(
                otherDisease,
                "early_blight",
                "Early blight",
                OTHER_CROP,
                false,
                false,
                List.of(new DiseaseSymptomWeight(otherDisease, S1, new BigDecimal("0.8"))));
        ScorableDiseaseSheet unmatched = new ScorableDiseaseSheet(
                D,
                "blast",
                "Blast",
                CROP,
                false,
                false,
                List.of(new DiseaseSymptomWeight(D, S2, new BigDecimal("0.4"))));
        List<DiseaseScore> scores =
                DiseaseScorer.score(Map.of(S1, BigDecimal.ONE), List.of(otherCrop, unmatched), CROP);
        assertThat(scores).isEmpty();
    }

    @Test
    void ranksEqualScoresByCode() {
        UUID da = UUID.fromString("01800000-0000-7000-8000-000000000102");
        UUID db = UUID.fromString("01800000-0000-7000-8000-000000000103");
        ScorableDiseaseSheet b = new ScorableDiseaseSheet(
                db,
                "b",
                "B",
                CROP,
                false,
                false,
                List.of(new DiseaseSymptomWeight(db, S1, BigDecimal.ONE)));
        ScorableDiseaseSheet a = new ScorableDiseaseSheet(
                da,
                "a",
                "A",
                CROP,
                false,
                false,
                List.of(new DiseaseSymptomWeight(da, S1, BigDecimal.ONE)));
        List<DiseaseScore> scores = DiseaseScorer.score(Map.of(S1, new BigDecimal("0.90")), List.of(b, a), CROP);
        assertThat(scores.getFirst().code()).isEqualTo("a");
        assertThat(scores.getFirst().rank()).isEqualTo(1);
        assertThat(scores.get(1).code()).isEqualTo("b");
        assertThat(scores.get(1).rank()).isEqualTo(2);
    }
}
