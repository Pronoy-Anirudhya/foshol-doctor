package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpSpeechToTextAdapterTest {

    @Mock
    SidecarHttpClient http;

    @Test
    void postsMultipartAudioAndParsesTranscript() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"model_id":"bangla-whisper","model_version":"abc123",\
                        "transcript":"TODO(content-owner)","confidence":0.812,\
                        "inference_ms":6100}
                        """);
        byte[] audio = {1, 2, 3};
        when(http.readBytes("obj")).thenReturn(audio);
        when(http.postMultipartAudio(eq("/v1/asr/transcribe"), eq(audio), eq("audio.bin"), eq("corr")))
                .thenReturn(node);

        HttpSpeechToTextAdapter adapter = new HttpSpeechToTextAdapter(http, support());
        TranscriptResult result = adapter.transcribe(request());

        assertThat(result.modelId()).isEqualTo("bangla-whisper");
        assertThat(result.transcriptBn()).isEqualTo("TODO(content-owner)");
        assertThat(result.asrConfidence()).isEqualByComparingTo(new BigDecimal("0.812"));
        assertThat(result.latencyMs()).isEqualTo(6100);
        verify(http, times(1))
                .postMultipartAudio(eq("/v1/asr/transcribe"), eq(audio), eq("audio.bin"), eq("corr"));
        verify(http, times(1)).readBytes("obj");
    }

    @Test
    void acceptsTranscriptAndConfidenceFallbacks() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"modelId":"bangla-whisper","transcript_bn":"TODO(content-owner)",\
                        "asrConfidence":0.91,"latency_ms":20}
                        """);
        byte[] audio = {9, 8, 7};
        when(http.readBytes("obj")).thenReturn(audio);
        when(http.postMultipartAudio(eq("/v1/asr/transcribe"), any(), eq("audio.bin"), eq("corr")))
                .thenReturn(node);

        HttpSpeechToTextAdapter adapter = new HttpSpeechToTextAdapter(http, support());
        TranscriptResult result = adapter.transcribe(request());

        assertThat(result.modelId()).isEqualTo("bangla-whisper");
        assertThat(result.transcriptBn()).isEqualTo("TODO(content-owner)");
        assertThat(result.asrConfidence()).isEqualByComparingTo(new BigDecimal("0.91"));
        assertThat(result.latencyMs()).isEqualTo(20);
    }

    @Test
    void transcribesRawAudioWithoutObjectStore() throws Exception {
        JsonNode node = new ObjectMapper()
                .readTree(
                        """
                        {"model_id":"bangla-whisper","transcript":"blast","confidence":0.91,\
                        "inference_ms":20}
                        """);
        byte[] audio = {1, 2, 3};
        when(http.postMultipartAudio(
                        eq("/v1/asr/transcribe"), eq(audio), eq("audio.bin"), eq("corr"), eq("bn")))
                .thenReturn(node);

        HttpSpeechToTextAdapter adapter = new HttpSpeechToTextAdapter(http, support());
        TranscriptResult result = adapter.transcribeAudio(audio, "bn", "corr");

        assertThat(result.transcriptBn()).isEqualTo("blast");
        assertThat(result.asrConfidence()).isEqualByComparingTo(new BigDecimal("0.91"));
        verify(http, never()).readBytes(any());
    }

    private static TranscriptRequest request() {
        return new TranscriptRequest(UUID.randomUUID(), UUID.randomUUID(), "obj", "abc123", 1000, "corr");
    }

    private static SidecarCallSupport support() {
        return new SidecarCallSupport(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()),
                new SimpleMeterRegistry());
    }
}
