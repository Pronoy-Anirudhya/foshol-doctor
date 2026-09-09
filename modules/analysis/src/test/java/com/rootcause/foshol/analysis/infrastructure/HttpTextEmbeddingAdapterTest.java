package com.rootcause.foshol.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.HttpTextEmbeddingAdapter;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.SidecarHttpClient;
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
class HttpTextEmbeddingAdapterTest {

    @Mock
    SidecarHttpClient http;

    @Test
    void postsTextsArrayAndReadsFirstEmbedding() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"model_id":"sentence-transformers/LaBSE","model_version":"v1",\
                        "dimension":768,"normalised":true,\
                        "embeddings":[[0.0123,-0.45,0.78]],"inference_ms":95}
                        """);
        ObjectNode requestBody = new ObjectMapper().createObjectNode();
        when(http.object()).thenReturn(requestBody);
        when(http.postJson(eq("/v1/embed"), any(), eq("corr"))).thenReturn(node);

        HttpTextEmbeddingAdapter adapter = new HttpTextEmbeddingAdapter(http, support());
        EmbeddingResult result = adapter.embed(new EmbeddingRequest(UUID.randomUUID(), "nfc-text", "corr"));

        verify(http, times(1)).postJson(eq("/v1/embed"), eq(requestBody), eq("corr"));
        assertThat(requestBody.has("text")).isFalse();
        assertThat(requestBody.get("texts").isArray()).isTrue();
        assertThat(requestBody.get("texts").size()).isEqualTo(1);
        assertThat(requestBody.get("texts").get(0).asText()).isEqualTo("nfc-text");

        assertThat(result.modelId()).isEqualTo("sentence-transformers/LaBSE");
        assertThat(result.latencyMs()).isEqualTo(95);
        assertThat(result.vector()).hasSize(3);
        assertThat(result.vector()[0]).isCloseTo(0.0123f, within(0.0001f));
        assertThat(result.vector()[1]).isCloseTo(-0.45f, within(0.0001f));
        assertThat(result.vector()[2]).isCloseTo(0.78f, within(0.0001f));
    }

    @Test
    void acceptsVectorFallbackAndCamelModelId() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"modelId":"labse","vector":[0.1,0.2],"latency_ms":10}
                        """);
        when(http.object()).thenReturn(new ObjectMapper().createObjectNode());
        when(http.postJson(eq("/v1/embed"), any(), eq("corr"))).thenReturn(node);

        HttpTextEmbeddingAdapter adapter = new HttpTextEmbeddingAdapter(http, support());
        EmbeddingResult result = adapter.embed(new EmbeddingRequest(UUID.randomUUID(), "nfc-text", "corr"));

        assertThat(result.modelId()).isEqualTo("labse");
        assertThat(result.latencyMs()).isEqualTo(10);
        assertThat(result.vector()).containsExactly(0.1f, 0.2f);
    }

    private static SidecarCallSupport support() {
        return new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()),
                new SimpleMeterRegistry());
    }
}
