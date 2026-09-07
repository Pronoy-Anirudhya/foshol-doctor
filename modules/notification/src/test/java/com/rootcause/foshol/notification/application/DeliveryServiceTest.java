package com.rootcause.foshol.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.domain.ChannelNames;
import com.rootcause.foshol.notification.domain.DeliveryState;
import com.rootcause.foshol.notification.domain.Notification;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DeliveryServiceTest {

    @Test
    void firstSuccessWins() {
        RecordingChannel sse = new RecordingChannel(10, ChannelNames.SSE, true, true, true);
        RecordingChannel push = new RecordingChannel(20, ChannelNames.WEB_PUSH, true, true, true);
        DeliveryService service = service(List.of(push, sse));
        Notification pending = pending();
        service.deliver(pending);
        assertThat(pending.state()).isEqualTo(DeliveryState.SENT);
        assertThat(pending.channel()).isEqualTo(ChannelNames.SSE);
        assertThat(sse.sends.get()).isEqualTo(1);
        assertThat(push.sends.get()).isZero();
    }

    @Test
    void skippedWhenNothingSupports() {
        RecordingChannel sse = new RecordingChannel(10, ChannelNames.SSE, true, false, true);
        RecordingChannel push = new RecordingChannel(20, ChannelNames.WEB_PUSH, false, false, true);
        DeliveryService service = service(List.of(sse, push));
        Notification pending = pending();
        service.deliver(pending);
        assertThat(pending.state()).isEqualTo(DeliveryState.SKIPPED);
        assertThat(pending.attempts()).isZero();
        assertThat(sse.sends.get()).isZero();
    }

    @Test
    void throwingChannelIsWarnAndContinue() {
        NotificationChannel boom = new ThrowingChannel();
        RecordingChannel next = new RecordingChannel(20, ChannelNames.WEB_PUSH, true, true, true);
        DeliveryService service = service(List.of(boom, next));
        Notification pending = pending();
        service.deliver(pending);
        assertThat(pending.state()).isEqualTo(DeliveryState.SENT);
        assertThat(next.sends.get()).isEqualTo(1);
    }

    @Test
    void allFailMarksFailed() {
        RecordingChannel sse = new RecordingChannel(10, ChannelNames.SSE, true, true, false);
        DeliveryService service = service(List.of(sse));
        Notification pending = pending();
        service.deliver(pending);
        assertThat(pending.state()).isEqualTo(DeliveryState.FAILED);
        assertThat(pending.attempts()).isEqualTo((short) 1);
    }

    @Test
    void disabledChannelsAreNotCalled() {
        RecordingChannel push = new RecordingChannel(20, ChannelNames.WEB_PUSH, false, true, true);
        RecordingChannel sms = new RecordingChannel(30, ChannelNames.SMS, false, true, true);
        RecordingChannel sse = new RecordingChannel(10, ChannelNames.SSE, true, true, true);
        DeliveryService service = service(List.of(sse, push, sms));
        service.deliver(pending());
        assertThat(push.sends.get()).isZero();
        assertThat(sms.sends.get()).isZero();
        assertThat(sse.sends.get()).isEqualTo(1);
    }

    private static DeliveryService service(List<NotificationChannel> channels) {
        NotificationRepository repo = mock(NotificationRepository.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return new DeliveryService(repo, channels, Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC), tm);
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

    private static final class RecordingChannel implements NotificationChannel, Ordered {
        private final int order;
        private final String name;
        private final boolean enabled;
        private final boolean supports;
        private final boolean result;
        private final AtomicInteger sends = new AtomicInteger();

        private RecordingChannel(int order, String name, boolean enabled, boolean supports, boolean result) {
            this.order = order;
            this.name = name;
            this.enabled = enabled;
            this.supports = supports;
            this.result = result;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean supports(UUID farmerId) {
            return supports;
        }

        @Override
        public boolean send(AdvisoryNotification notification) {
            sends.incrementAndGet();
            return result;
        }
    }

    private static final class ThrowingChannel implements NotificationChannel, Ordered {
        @Override
        public int getOrder() {
            return 10;
        }

        @Override
        public String name() {
            return ChannelNames.SSE;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public boolean supports(UUID farmerId) {
            return true;
        }

        @Override
        public boolean send(AdvisoryNotification notification) {
            throw new IllegalStateException("boom");
        }
    }
}
