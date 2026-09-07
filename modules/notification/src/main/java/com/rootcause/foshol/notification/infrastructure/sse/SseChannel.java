package com.rootcause.foshol.notification.infrastructure.sse;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.domain.ChannelNames;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class SseChannel implements NotificationChannel {

    private final SseSubscriptionRegistry registry;
    private final boolean enabled;

    public SseChannel(
            SseSubscriptionRegistry registry,
            @Value("${" + ConfigKeys.CHANNELS_SSE_ENABLED + ":true}") boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;
    }

    @Override
    public String name() {
        return ChannelNames.SSE;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean supports(UUID farmerId) {
        return registry.farmerConnected(farmerId);
    }

    @Override
    public boolean send(AdvisoryNotification notification) {
        return registry.sendToFarmer(notification);
    }
}
