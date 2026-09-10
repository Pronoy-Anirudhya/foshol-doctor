package com.rootcause.foshol.notification.infrastructure.channel;

import com.rootcause.foshol.common.contract.ConfigKeys;
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
@Order(30)
public class SmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    private final boolean enabled;

    public SmsChannel(@Value("${" + ConfigKeys.CHANNELS_SMS_ENABLED + ":false}") boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            log.warn("SMS is enabled but has no transport configured");
        }
    }

    @Override
    public String name() {
        return ChannelNames.SMS;
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
        log.info("SMS {} case {}", notification.type(), notification.caseId());
        return false;
    }
}
