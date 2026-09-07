package com.rootcause.foshol.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.notification.api.NotificationChannel;
import com.rootcause.foshol.notification.infrastructure.channel.SmsChannel;
import com.rootcause.foshol.notification.infrastructure.channel.WebPushChannel;
import com.rootcause.foshol.notification.infrastructure.sse.SseChannel;
import com.rootcause.foshol.notification.infrastructure.sse.SseSubscriptionRegistry;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = NotificationChannelBeansTest.Config.class)
class NotificationChannelBeansTest {

    @Autowired
    private List<NotificationChannel> channels;

    @Test
    void exactlyThreeChannelsInDeclaredOrder() {
        List<NotificationChannel> ordered = channels.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
        assertThat(ordered).hasSize(3);
        assertThat(ordered.get(0)).isInstanceOf(SseChannel.class);
        assertThat(ordered.get(1)).isInstanceOf(WebPushChannel.class);
        assertThat(ordered.get(2)).isInstanceOf(SmsChannel.class);
        assertThat(SseChannel.class.getAnnotation(Order.class).value()).isEqualTo(10);
        assertThat(WebPushChannel.class.getAnnotation(Order.class).value()).isEqualTo(20);
        assertThat(SmsChannel.class.getAnnotation(Order.class).value()).isEqualTo(30);
    }

    @Configuration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        SseSubscriptionRegistry registry(Clock clock) {
            return new SseSubscriptionRegistry(clock);
        }

        @Bean
        SseChannel sseChannel(SseSubscriptionRegistry registry) {
            return new SseChannel(registry, true);
        }

        @Bean
        WebPushChannel webPushChannel() {
            return new WebPushChannel(false);
        }

        @Bean
        SmsChannel smsChannel() {
            return new SmsChannel(false);
        }
    }
}
