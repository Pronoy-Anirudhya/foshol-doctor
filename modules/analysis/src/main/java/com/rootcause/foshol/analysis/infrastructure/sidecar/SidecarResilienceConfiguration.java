package com.rootcause.foshol.analysis.infrastructure.sidecar;

import com.rootcause.foshol.analysis.application.config.AnalysisSettings;
import io.github.resilience4j.common.timelimiter.configuration.TimeLimiterConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SidecarResilienceConfiguration {

    @Bean
    TimeLimiterConfigCustomizer sidecarTimeLimiter(AnalysisSettings settings) {
        return TimeLimiterConfigCustomizer.of(
                SidecarCallSupport.INSTANCE,
                builder -> builder.timeoutDuration(settings.aiTimeout()).cancelRunningFuture(true));
    }
}
