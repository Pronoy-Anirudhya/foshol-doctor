package com.rootcause.foshol.analysis.infrastructure.sidecar;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SidecarCallSupportTimeoutTest {

    @Test
    void timesOutUsingConfiguredDurationNotRegistryDefault() {
        SidecarCallSupport support = new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                Duration.ofMillis(200));
        assertThatThrownBy(() -> support.execute("vision", () -> {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                }))
                .isInstanceOf(SidecarFailureException.class)
                .extracting(ex -> ((SidecarFailureException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_SIDECAR_UNAVAILABLE);
    }
}
