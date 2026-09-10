package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.contract.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureEmbedAdapterTest {

    @Test
    void missingFixtureDoesNotInventAVector() {
        FixtureTextEmbeddingAdapter adapter = new FixtureTextEmbeddingAdapter(new ObjectMapper(), support());
        assertThatThrownBy(() ->
                        adapter.embed(new EmbeddingRequest(UUID.randomUUID(), "no-such-transcript", "corr")))
                .isInstanceOf(SidecarFailureException.class)
                .satisfies(ex -> assertThat(((SidecarFailureException) ex).errorCode())
                        .isEqualTo(ErrorCodes.ERR_FIXTURE_MISSING));
    }

    @Test
    void loadsClasspathFixtureBySha256OfTranscript() {
        FixtureTextEmbeddingAdapter adapter = new FixtureTextEmbeddingAdapter(new ObjectMapper(), support());
        EmbeddingResult result =
                adapter.embed(new EmbeddingRequest(UUID.randomUUID(), "TODO(content-owner)", "corr"));
        assertThat(result.vector()).hasSize(768);
    }

    private static SidecarCallSupport support() {
        return new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()),
                new SimpleMeterRegistry());
    }
}
