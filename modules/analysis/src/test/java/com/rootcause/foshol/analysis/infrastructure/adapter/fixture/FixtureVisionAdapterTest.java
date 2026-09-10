package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
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

class FixtureVisionAdapterTest {

    @Test
    void loadsClasspathFixtureBySha256() {
        FixtureVisionModelAdapter adapter = new FixtureVisionModelAdapter(new ObjectMapper(), support());
        VisionResult result = adapter.classify(request("8b3b8b23ce56af5cc3f864cf5fbca0f46b786901ec423237692c396cee9d1599"));
        assertThat(result.modelId()).isEqualTo("kssrikar4/Rice-Leaf-Disease-Classification");
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().get(0).rawLabel()).isEqualTo("Brown Spot");
    }

    @Test
    void missingFixtureDoesNotInventLabels() {
        FixtureVisionModelAdapter adapter = new FixtureVisionModelAdapter(new ObjectMapper(), support());
        assertThatThrownBy(() -> adapter.classify(request("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")))
                .isInstanceOf(SidecarFailureException.class)
                .satisfies(ex -> assertThat(((SidecarFailureException) ex).errorCode())
                        .isEqualTo(ErrorCodes.ERR_FIXTURE_MISSING));
    }

    private static VisionRequest request(String sha256) {
        return new VisionRequest(UUID.randomUUID(), UUID.randomUUID(), "rice", "obj", sha256, "corr");
    }

    private static SidecarCallSupport support() {
        return new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()),
                new SimpleMeterRegistry());
    }
}
