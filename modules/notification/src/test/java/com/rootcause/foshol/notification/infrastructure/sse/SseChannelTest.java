package com.rootcause.foshol.notification.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.domain.ChannelNames;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseChannelTest {

    @Test
    void contract() {
        SseSubscriptionRegistry registry =
                new SseSubscriptionRegistry(Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
        SseChannel channel = new SseChannel(registry, true);
        assertThat(channel.name()).isEqualTo(ChannelNames.SSE);
        assertThat(channel.enabled()).isTrue();
        assertThat(channel.supports(NotifyFixtures.FARMER)).isFalse();
        CapturingEmitter emitter = new CapturingEmitter();
        registry.attach(NotifyFixtures.FARMER, Role.FARMER, null, emitter);
        assertThat(channel.supports(NotifyFixtures.FARMER)).isTrue();
        assertThat(channel.send(NotifyFixtures.advisoryNotification())).isTrue();
        assertThat(emitter.payloads).isNotEmpty();
        SseChannel disabled = new SseChannel(registry, false);
        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void sixthConnectionClosesOldest() {
        SseSubscriptionRegistry registry =
                new SseSubscriptionRegistry(Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
        for (int i = 0; i < 6; i++) {
            registry.subscribe(NotifyFixtures.FARMER, Role.FARMER, null, Duration.ofMinutes(30));
        }
        assertThat(registry.count(NotifyFixtures.FARMER)).isEqualTo(5);
    }

    @Test
    void lastEventIdEmitsResyncFirst() {
        SseSubscriptionRegistry registry =
                new SseSubscriptionRegistry(Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
        CapturingEmitter emitter = new CapturingEmitter();
        registry.attach(NotifyFixtures.FARMER, Role.FARMER, NotifyFixtures.ADVISORY.toString(), emitter);
        assertThat(emitter.payloads.getFirst()).contains("resync");
    }

    static final class CapturingEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        CapturingEmitter() {
            super(Long.MAX_VALUE);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(part -> payloads.add(String.valueOf(part.getData())));
        }
    }
}
