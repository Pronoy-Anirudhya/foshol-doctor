package com.rootcause.foshol.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.RejectionReason;
import com.rootcause.foshol.common.Uuid7;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaseRejectionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void storesOfficerAuthoredMessage() {
        CaseRejection rejection = new CaseRejection(
                Uuid7.create(), Uuid7.create(), Uuid7.create(), RejectionReason.BLURRY_IMAGE, "ছবি", T0);
        assertThat(rejection.messageBn()).isEqualTo("ছবি");
        assertThat(rejection.reasonCode()).isEqualTo(RejectionReason.BLURRY_IMAGE);
    }

    @Test
    void blankMessageIsRejected() {
        assertThatThrownBy(() -> new CaseRejection(
                        Uuid7.create(), Uuid7.create(), Uuid7.create(), RejectionReason.OTHER, "   ", T0))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void messageOver500IsRejected() {
        assertThatThrownBy(() -> new CaseRejection(
                        Uuid7.create(),
                        Uuid7.create(),
                        Uuid7.create(),
                        RejectionReason.OTHER,
                        "x".repeat(501),
                        T0))
                .isInstanceOf(ReviewException.class);
    }
}
