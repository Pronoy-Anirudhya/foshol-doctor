package com.rootcause.foshol.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.AdvisoryAction;
import com.rootcause.foshol.common.Uuid7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdvisoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void firstVersionHasNullSupersedes() {
        Advisory advisory = first();
        assertThat(advisory.version()).isEqualTo((short) 1);
        assertThat(advisory.supersedesId()).isNull();
    }

    @Test
    void reviseIncrementsVersionAndPointsAtPredecessor() {
        Advisory v1 = first();
        Advisory v2 = v1.revise(
                Uuid7.create(), v1.diseaseId(), v1.officerId(), AdvisoryAction.EDITED, null, List.of(), T0.plusSeconds(1));
        assertThat(v2.version()).isEqualTo((short) 2);
        assertThat(v2.supersedesId()).isEqualTo(v1.id());
        assertThat(v1.version()).isEqualTo((short) 1);
        assertThat(v1.supersedesId()).isNull();
    }

    @Test
    void versionTwoWithNullSupersedesIsRejected() {
        assertThatThrownBy(() -> new Advisory(
                        Uuid7.create(),
                        Uuid7.create(),
                        Uuid7.create(),
                        Uuid7.create(),
                        AdvisoryAction.APPROVED,
                        null,
                        (short) 2,
                        null,
                        List.of(),
                        T0,
                        T0,
                        "officer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diseaseIdIsRequired() {
        assertThatThrownBy(() -> Advisory.firstVersion(
                        Uuid7.create(),
                        Uuid7.create(),
                        null,
                        Uuid7.create(),
                        AdvisoryAction.APPROVED,
                        null,
                        List.of(),
                        T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediesPreserveOrder() {
        UUID r1 = Uuid7.create();
        UUID r2 = Uuid7.create();
        Advisory advisory = Advisory.firstVersion(
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                AdvisoryAction.EDITED,
                null,
                List.of(new AdvisoryRemedy(r1, (short) 0), new AdvisoryRemedy(r2, (short) 1)),
                T0);
        assertThat(advisory.remedies()).extracting(AdvisoryRemedy::remedyId).containsExactly(r1, r2);
    }

    private static Advisory first() {
        return Advisory.firstVersion(
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                AdvisoryAction.APPROVED,
                null,
                List.of(),
                T0);
    }
}
