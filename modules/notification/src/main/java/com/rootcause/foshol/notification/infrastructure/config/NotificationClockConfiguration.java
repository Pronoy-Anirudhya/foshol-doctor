package com.rootcause.foshol.notification.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class NotificationClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
