package com.rootcause.foshol.review.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecificationsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final UUID OFFICER = Uuid7.create();

    @Test
    void taskIsClaimablePendingAndLiveHolder() {
        ReviewTask pending = ReviewTask.createPending(Uuid7.create(), Uuid7.create(), null, T0, T0);
        assertThat(new TaskIsClaimable(OFFICER, T0, TTL).isSatisfiedBy(pending)).isTrue();
        pending.claim(OFFICER, T0, TTL);
        assertThat(new TaskIsClaimable(OFFICER, T0, TTL).isSatisfiedBy(pending)).isTrue();
        assertThat(new TaskIsClaimable(Uuid7.create(), T0, TTL).isSatisfiedBy(pending)).isFalse();
    }

    @Test
    void claimIsHeldByRequiresLiveMatch() {
        ReviewTask task = ReviewTask.createPending(Uuid7.create(), Uuid7.create(), null, T0, T0);
        task.claim(OFFICER, T0, TTL);
        assertThat(new ClaimIsHeldBy(OFFICER, T0, TTL).isSatisfiedBy(task)).isTrue();
        assertThat(new ClaimIsHeldBy(OFFICER, T0.plus(TTL).plusSeconds(1), TTL).isSatisfiedBy(task)).isFalse();
    }

    @Test
    void remediesBelongToDisease() {
        UUID disease = Uuid7.create();
        UUID r1 = Uuid7.create();
        RemedyView remedy = remedy(r1, disease, RemedyType.CULTURAL, 1);
        assertThat(new RemediesBelongToDisease(List.of(remedy))
                        .isSatisfiedBy(List.of(new AdvisoryRemedy(r1, (short) 0))))
                .isTrue();
        assertThat(new RemediesBelongToDisease(List.of(remedy))
                        .isSatisfiedBy(List.of(new AdvisoryRemedy(Uuid7.create(), (short) 0))))
                .isFalse();
    }

    @Test
    void advisoryHasRequiredRemedy() {
        DiseaseView healthy = disease(true);
        DiseaseView sick = disease(false);
        assertThat(new AdvisoryHasRequiredRemedy(healthy).isSatisfiedBy(List.of())).isTrue();
        assertThat(new AdvisoryHasRequiredRemedy(sick).isSatisfiedBy(List.of())).isFalse();
        assertThat(new AdvisoryHasRequiredRemedy(sick).isSatisfiedBy(List.of(new AdvisoryRemedy(Uuid7.create(), (short) 0))))
                .isTrue();
    }

    @Test
    void chemicalRemedyHasPhi() {
        UUID id = Uuid7.create();
        RemedyView ok = remedy(id, Uuid7.create(), RemedyType.CHEMICAL, 7);
        RemedyView missing = remedy(Uuid7.create(), Uuid7.create(), RemedyType.CHEMICAL, null);
        assertThat(new ChemicalRemedyHasPhi(List.of(ok)).isSatisfiedBy(List.of(id))).isTrue();
        assertThat(new ChemicalRemedyHasPhi(List.of(missing)).isSatisfiedBy(List.of(missing.id()))).isFalse();
    }

    @Test
    void taskIsTerminal() {
        ReviewTask task = ReviewTask.createPending(Uuid7.create(), Uuid7.create(), null, T0, T0);
        assertThat(new TaskIsTerminal().isSatisfiedBy(task)).isFalse();
        task.claim(OFFICER, T0, TTL);
        task.markDone(T0.plusSeconds(1), OFFICER.toString());
        assertThat(new TaskIsTerminal().isSatisfiedBy(task)).isTrue();
    }

    private static DiseaseView disease(boolean healthy) {
        return new DiseaseView(
                Uuid7.create(), Uuid7.create(), "code", "n", "n", null, Severity.NONE, healthy);
    }

    private static RemedyView remedy(UUID id, UUID diseaseId, RemedyType type, Integer phi) {
        return new RemedyView(id, diseaseId, type, "", List.of(), null, phi, "LOW", "LOW", "", null, null, null, null, null, null, null, null);
    }
}
