package com.rootcause.foshol.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.notification.NotifyFixtures;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void sentIffDeliveredAt() {
        Notification pending = pending();
        assertThat(pending.deliveredAt()).isNull();
        pending.markSent(ChannelNames.SSE, NotifyFixtures.T0);
        assertThat(pending.state()).isEqualTo(DeliveryState.SENT);
        assertThat(pending.deliveredAt()).isEqualTo(NotifyFixtures.T0);
        assertThatThrownBy(() -> new Notification(
                        Uuid7.create(),
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        NotifyFixtures.ADVISORY,
                        ChannelNames.SSE,
                        NotificationType.ADVISORY_PUBLISHED,
                        "t",
                        "b",
                        Map.of(),
                        DeliveryState.SENT,
                        (short) 1,
                        null,
                        NotifyFixtures.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skippedImpliesZeroAttempts() {
        Notification pending = pending();
        pending.markSkipped();
        assertThat(pending.state()).isEqualTo(DeliveryState.SKIPPED);
        assertThat(pending.attempts()).isZero();
        assertThatThrownBy(() -> new Notification(
                        Uuid7.create(),
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        NotifyFixtures.ADVISORY,
                        ChannelNames.SSE,
                        NotificationType.ADVISORY_PUBLISHED,
                        "t",
                        "b",
                        Map.of(),
                        DeliveryState.SKIPPED,
                        (short) 1,
                        null,
                        NotifyFixtures.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advisoryTypesRequireAdvisoryId() {
        assertThatThrownBy(() -> Notification.pending(
                        Uuid7.create(),
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        null,
                        NotificationType.ADVISORY_PUBLISHED,
                        "t",
                        "b",
                        Map.of(),
                        NotifyFixtures.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingSurvivingAttemptBecomesFailed() {
        Notification pending = pending();
        pending.ensureNotPending(NotifyFixtures.T0);
        assertThat(pending.state()).isEqualTo(DeliveryState.FAILED);
    }

    @Test
    void terminalStatesCannotTransition() {
        Notification pending = pending();
        pending.markFailed((short) 1);
        assertThatThrownBy(() -> pending.markSent(ChannelNames.SSE, Instant.parse("2026-01-01T00:00:01Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Notification pending() {
        return Notification.pending(
                Uuid7.create(),
                NotifyFixtures.FARMER,
                NotifyFixtures.CASE,
                NotifyFixtures.ADVISORY,
                NotificationType.ADVISORY_PUBLISHED,
                "t",
                "b",
                Map.of("correlationId", NotifyFixtures.CORRELATION),
                NotifyFixtures.T0);
    }
}
