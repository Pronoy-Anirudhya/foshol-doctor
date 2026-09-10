package com.rootcause.foshol.analysis.infrastructure.sidecar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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

    @Test
    void asrTimeoutIsSpeechBranchTimeoutNotUnavailable() {
        SidecarCallSupport support = new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                Duration.ofMillis(200));
        assertThatThrownBy(() -> support.execute("asr", () -> {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                }))
                .isInstanceOf(SidecarFailureException.class)
                .extracting(ex -> ((SidecarFailureException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT);
    }

    @Test
    void asrUsesDeadlineRatherThanVisionTimeout() {
        SidecarCallSupport support = new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                Duration.ofMillis(200),
                Duration.ofSeconds(2));
        String asr = support.execute("asr", () -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        });
        assertThat(asr).isEqualTo("ok");
        assertThatThrownBy(() -> support.execute("vision", () -> {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                }))
                .isInstanceOf(SidecarFailureException.class)
                .extracting(ex -> ((SidecarFailureException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_SIDECAR_UNAVAILABLE);
    }

    @Test
    void asrTimeoutsDoNotOpenTheSidecarCircuit() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(6)
                .minimumNumberOfCalls(6)
                .failureRateThreshold(50f)
                .build();
        SidecarCallSupport support = new SidecarCallSupport(
                CircuitBreakerRegistry.of(config),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                Duration.ofMillis(80));
        for (int i = 0; i < 8; i++) {
            assertThatThrownBy(() -> support.execute("asr", () -> {
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        return "x";
                    }))
                    .isInstanceOf(SidecarFailureException.class)
                    .extracting(ex -> ((SidecarFailureException) ex).errorCode())
                    .isEqualTo(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT);
        }
        assertThat(support.execute("vision", () -> "ok")).isEqualTo("ok");
    }
}
