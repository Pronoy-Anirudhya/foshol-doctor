package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
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
class HttpVisionAdapterTest {

    @Mock
    SidecarHttpClient http;

    @Test
    void postsMultipartAndParsesPredictions() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"model_id":"wambugu71/crop_leaf_diseases_vit",\
                        "model_version":"7d5b32bcd6f83a2f57e7e0346358fad276296877",\
                        "predictions":[{"raw_label":"Rice___Leaf_Blast","confidence":0.91,"rank":1}],\
                        "inference_ms":12}
                        """);
        when(http.readBytes("obj")).thenReturn(new byte[] {1, 2, 3});
        when(http.postMultipart(eq("/v1/vision/classify"), any(), eq("leaf.bin"), eq("rice"), eq("corr")))
                .thenReturn(node);

        HttpVisionModelAdapter adapter = new HttpVisionModelAdapter(http, support());
        VisionResult result = adapter.classify(request("abc123"));

        assertThat(result.modelId()).isEqualTo("wambugu71/crop_leaf_diseases_vit");
        assertThat(result.modelVersion()).isEqualTo("7d5b32bcd6f83a2f57e7e0346358fad276296877");
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).rawLabel()).isEqualTo("Rice___Leaf_Blast");
        assertThat(result.latencyMs()).isEqualTo(12);
        verify(http, times(1)).postMultipart(eq("/v1/vision/classify"), any(), eq("leaf.bin"), eq("rice"), eq("corr"));
    }

    @Test
    void cacheSkipsSecondHttpCallForSameDigest() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"model_id":"wambugu71/crop_leaf_diseases_vit",\
                        "model_version":"7d5b32bcd6f83a2f57e7e0346358fad276296877",\
                        "predictions":[{"raw_label":"Rice___Brown_Spot","confidence":0.80,"rank":1}],\
                        "inference_ms":40}
                        """);
        when(http.readBytes("obj")).thenReturn(new byte[] {9, 8, 7});
        when(http.postMultipart(eq("/v1/vision/classify"), any(), eq("leaf.bin"), eq("rice"), eq("corr")))
                .thenReturn(node);

        HttpVisionModelAdapter adapter = new HttpVisionModelAdapter(http, support());
        VisionRequest request = request("deadbeef");
        VisionResult first = adapter.classify(request);
        VisionResult second = adapter.classify(request);

        assertThat(second.candidates().get(0).rawLabel()).isEqualTo(first.candidates().get(0).rawLabel());
        verify(http, times(1)).postMultipart(any(), any(), any(), any(), any());
        verify(http, times(1)).readBytes("obj");
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
