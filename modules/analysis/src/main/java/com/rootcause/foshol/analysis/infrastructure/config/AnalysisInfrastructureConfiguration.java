package com.rootcause.foshol.analysis.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.common.jdbc.ReadOnlyDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AnalysisInfrastructureConfiguration {

    @Bean
    @ReadOnlyDataSource
    DataSource analysisReadOnlyDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper analysisJackson2ObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry analysisMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
    CircuitBreakerRegistry analysisCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    @ConditionalOnMissingBean(RetryRegistry.class)
    RetryRegistry analysisRetryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    @Bean
    @ConditionalOnMissingBean(TimeLimiterRegistry.class)
    TimeLimiterRegistry analysisTimeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }
}
