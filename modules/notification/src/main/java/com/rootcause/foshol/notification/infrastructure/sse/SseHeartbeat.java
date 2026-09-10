package com.rootcause.foshol.notification.infrastructure.sse;

import com.rootcause.foshol.common.contract.ConfigKeys;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class SseHeartbeat {

    private final SseSubscriptionRegistry registry;

    public SseHeartbeat(
            SseSubscriptionRegistry registry,
            @Value("${" + ConfigKeys.CHANNELS_SSE_HEARTBEAT + ":PT20S}") Duration ignored) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${" + ConfigKeys.CHANNELS_SSE_HEARTBEAT + "}")
    public void beat() {
        registry.heartbeat();
    }
}
