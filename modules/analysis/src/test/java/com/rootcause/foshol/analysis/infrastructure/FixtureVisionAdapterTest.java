package com.rootcause.foshol.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.VisionBatchRequest;
import com.rootcause.foshol.analysis.application.port.VisionBatchResult;
import com.rootcause.foshol.analysis.application.port.VisionImageRef;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.fixture.FixtureVisionModelAdapter;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ErrorCodes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureVisionAdapterTest {

    static final String HIGH_SHA = "8b3b8b23ce56af5cc3f864cf5fbca0f46b786901ec423237692c396cee9d1599";
    static final String HIGH_B_SHA = "9ee38e5617b0f15aed8951c05cc2de934295d5209a7bcfda86d0a589272f3dd9";

    @Test
    void loadsClasspathFixtureBySha256() {
        FixtureVisionModelAdapter adapter = new FixtureVisionModelAdapter(new ObjectMapper(), support());
        VisionResult result = adapter.classify(request(HIGH_SHA));
        assertThat(result.modelId()).isEqualTo("kssrikar4/Rice-Leaf-Disease-Classification");
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().get(0).rawLabel()).isEqualTo("Brown Spot");
    }

    @Test
    void classifyBatchLoadsEachFixtureOnce() {
        FixtureVisionModelAdapter adapter = new FixtureVisionModelAdapter(new ObjectMapper(), support());
        UUID imageA = UUID.randomUUID();
        UUID imageB = UUID.randomUUID();
        VisionBatchResult batch = adapter.classifyBatch(new VisionBatchRequest(
                UUID.randomUUID(),
                "rice",
                "corr",
                List.of(
                        new VisionImageRef(imageA, "obj-a", HIGH_SHA),
                        new VisionImageRef(imageB, "obj-b", HIGH_B_SHA))));
        assertThat(batch.images()).hasSize(2);
        assertThat(batch.images().get(0).imageId()).isEqualTo(imageA);
        assertThat(batch.images().get(1).imageId()).isEqualTo(imageB);
        assertThat(batch.images().get(0).candidates()).isNotEmpty();
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
