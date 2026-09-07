package com.rootcause.foshol.notification.infrastructure.channel;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.domain.ChannelNames;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class WebPushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WebPushChannel.class);

    private final boolean enabled;

    public WebPushChannel(@Value("${" + ConfigKeys.CHANNELS_WEBPUSH_ENABLED + ":false}") boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            log.warn("WEB_PUSH is enabled but has no transport configured");
        }
    }

    @Override
    public String name() {
        return ChannelNames.WEB_PUSH;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean supports(UUID farmerId) {
        return false;
    }

    @Override
    public boolean send(AdvisoryNotification notification) {
        log.info("WEB_PUSH {} case {}", notification.type(), notification.caseId());
        return false;
    }
}
