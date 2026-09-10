package com.rootcause.foshol.config;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourcePrimaryConfiguration {

    /**
     * Analysis, review and notification each register a {@code DataSource} alias for the
     * read replica. That makes Boot skip its pooled {@code dataSource} bean
     * ({@code @ConditionalOnMissingBean}) and skip JPA ({@code @ConditionalOnSingleCandidate}).
     * An explicit primary datasource restores both.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
