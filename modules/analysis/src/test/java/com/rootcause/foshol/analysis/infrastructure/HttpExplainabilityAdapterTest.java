package com.rootcause.foshol.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.application.port.ExplanationRequest;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.HttpExplainabilityAdapter;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.SidecarHttpClient;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpExplainabilityAdapterTest {

    @Mock
    SidecarHttpClient http;

    @Test
    void postsMultipartPng() {
        byte[] png = {(byte) 0x89, 0x50};
        when(http.readBytes("obj")).thenReturn(new byte[] {1, 2, 3});
        when(http.postMultipartPng(
                        eq("/v1/vision/explain"), any(), eq("leaf.png"), eq("rice"), eq("Blast"), eq("corr")))
                .thenReturn(new ExplanationResult("vit", "1", png, "image/png", 0));

        HttpExplainabilityAdapter adapter = new HttpExplainabilityAdapter(http, support());
        ExplanationResult result = adapter.explain(request());

        assertThat(result.overlayPng()).isEqualTo(png);
        assertThat(result.modelId()).isEqualTo("vit");
        verify(http, never()).postJson(any(), any(), any());
    }

    @Test
    void liveModelUnavailableDoesNotPropagate() {
        when(http.readBytes("obj")).thenReturn(new byte[] {1});
        when(http.postMultipartPng(any(), any(), any(), any(), any(), any()))
                .thenThrow(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_MODEL_UNAVAILABLE, "no overlay"));

        HttpExplainabilityAdapter adapter = new HttpExplainabilityAdapter(http, support());
        assertThat(adapter.explain(request())).isNull();
    }

    private static ExplanationRequest request() {
        return new ExplanationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "rice", "obj", "abc123", "Blast", "corr");
    }

    private static SidecarCallSupport support() {
        return new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()),
                new SimpleMeterRegistry());
    }
}
