package com.rootcause.foshol.review.infrastructure;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReviewPersistenceConfig {

    @Bean
    @ReadOnlyDataSource
    DataSource reviewReadOnlyDataSource(DataSource dataSource) {
        return dataSource;
    }
}
