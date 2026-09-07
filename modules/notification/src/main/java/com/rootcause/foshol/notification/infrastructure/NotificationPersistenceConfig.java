package com.rootcause.foshol.notification.infrastructure;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationPersistenceConfig {

    @Bean
    @ReadOnlyDataSource
    DataSource notificationReadOnlyDataSource(DataSource dataSource) {
        return dataSource;
    }
}
