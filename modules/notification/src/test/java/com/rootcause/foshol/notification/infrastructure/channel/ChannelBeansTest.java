package com.rootcause.foshol.notification.infrastructure.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.notification.domain.ChannelNames;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;

class ChannelBeansTest {

    @Test
    void webPushAndSmsAreDisabledAdapters() {
        WebPushChannel push = new WebPushChannel(false);
        SmsChannel sms = new SmsChannel(false);
        AdvisoryNotification n = new AdvisoryNotification(
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                Uuid7.create(),
                NotificationType.ADVISORY_PUBLISHED,
                "t",
                "b",
                Map.of());
        assertThat(push.name()).isEqualTo(ChannelNames.WEB_PUSH);
        assertThat(sms.name()).isEqualTo(ChannelNames.SMS);
        assertThat(push.enabled()).isFalse();
        assertThat(sms.enabled()).isFalse();
        assertThat(push.supports(Uuid7.create())).isFalse();
        assertThat(sms.supports(Uuid7.create())).isFalse();
        assertThat(push.send(n)).isFalse();
        assertThat(sms.send(n)).isFalse();
    }

    @Test
    void orderIsSseThenWebPushThenSms() {
        Order sse = com.rootcause.foshol.notification.infrastructure.sse.SseChannel.class.getAnnotation(Order.class);
        Order push = WebPushChannel.class.getAnnotation(Order.class);
        Order sms = SmsChannel.class.getAnnotation(Order.class);
        assertThat(sse.value()).isLessThan(push.value());
        assertThat(push.value()).isLessThan(sms.value());
        assertThat(AnnotationAwareOrderComparator.INSTANCE.compare(
                        new WebPushChannel(false), new SmsChannel(false)))
                .isLessThan(0);
    }
}
