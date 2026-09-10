package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.config.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SidecarHttpConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
    SidecarHttpClient sidecarHttpClient(
            AnalysisSettings settings, ObjectMapper mapper, ObjectStorePort objectStore) {
        return new SidecarHttpClient(settings, mapper, objectStore);
    }
}
